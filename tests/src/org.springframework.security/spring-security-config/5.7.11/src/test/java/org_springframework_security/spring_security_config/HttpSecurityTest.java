/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpSecurityTest {
    private GenericApplicationContext context;
    private HttpSecurity http;

    @BeforeEach
    void configureHttpSecurity() throws Exception {
        context = new GenericApplicationContext();
        context.refresh();
        ObjectPostProcessor<Object> processor = SecurityTestSupport.objectPostProcessor();
        AuthenticationManagerBuilder authentication = new AuthenticationManagerBuilder(processor);
        authentication.inMemoryAuthentication()
                .withUser("reader").password("{noop}read-secret").roles("READER")
                .and().withUser("admin").password("{noop}admin-secret").roles("ADMIN");
        http = new HttpSecurity(processor, authentication, Map.of(ApplicationContext.class, context));
        http.securityContext(Customizer.withDefaults());
        http.anonymous(Customizer.withDefaults());
        http.exceptionHandling(Customizer.withDefaults());
    }

    @AfterEach
    void closeContext() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void basicAuthenticationAndRoleRulesProtectRequests() throws Exception {
        http.authorizeHttpRequests(rules -> rules
                .antMatchers("/public/**").permitAll()
                .antMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated());
        http.httpBasic(basic -> basic.realmName("orders"));
        http.headers(headers -> headers.contentSecurityPolicy("default-src 'self'"));
        FilterChainProxy security = new FilterChainProxy(http.build());

        MockHttpServletRequest publicRequest = request("GET", "/public/catalog");
        publicRequest.setSecure(true);
        MockHttpServletResponse publicResponse = perform(security, publicRequest);
        assertThat(publicResponse.getStatus()).isEqualTo(200);
        assertThat(publicResponse.getContentAsString()).isEqualTo("anonymousUser");
        assertThat(publicResponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(publicResponse.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(publicResponse.getHeader("Content-Security-Policy")).isEqualTo("default-src 'self'");
        assertThat(publicResponse.getHeader("Strict-Transport-Security")).contains("max-age=");

        MockHttpServletResponse anonymous = perform(security, request("GET", "/orders"));
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(anonymous.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"orders\"");

        MockHttpServletResponse reader = perform(security, authenticatedRequest("/orders", "reader:read-secret"));
        assertThat(reader.getStatus()).isEqualTo(200);
        assertThat(reader.getContentAsString()).isEqualTo("reader");

        MockHttpServletResponse forbidden = perform(security, authenticatedRequest("/admin/orders", "reader:read-secret"));
        assertThat(forbidden.getStatus()).isEqualTo(403);

        MockHttpServletResponse admin = perform(security, authenticatedRequest("/admin/orders", "admin:admin-secret"));
        assertThat(admin.getStatus()).isEqualTo(200);
        assertThat(admin.getContentAsString()).isEqualTo("admin");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidBasicCredentialsAreRejected() throws Exception {
        http.authorizeHttpRequests(rules -> rules.anyRequest().authenticated());
        http.httpBasic(basic -> basic.realmName("orders"));
        FilterChainProxy security = new FilterChainProxy(http.build());

        MockHttpServletResponse incorrect = perform(security, authenticatedRequest("/orders", "reader:incorrect"));
        assertThat(incorrect.getStatus()).isEqualTo(401);
        assertThat(incorrect.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"orders\"");
        MockHttpServletResponse unknown = perform(security, authenticatedRequest("/orders", "unknown:read-secret"));
        assertThat(unknown.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void csrfRequiresSessionTokenForStateChangingRequests() throws Exception {
        http.authorizeHttpRequests(rules -> rules.anyRequest().permitAll());
        http.csrf(Customizer.withDefaults());
        FilterChainProxy security = new FilterChainProxy(http.build());

        MockHttpServletRequest form = request("GET", "/orders/new");
        assertThat(perform(security, form).getStatus()).isEqualTo(200);
        CsrfToken token = (CsrfToken) form.getAttribute(CsrfToken.class.getName());
        assertThat(token).isNotNull();
        assertThat(token.getToken()).isNotBlank();
        MockHttpSession session = (MockHttpSession) form.getSession(false);
        assertThat(session).isNotNull();

        MockHttpServletRequest missing = request("POST", "/orders");
        missing.setSession(session);
        assertThat(perform(security, missing).getStatus()).isEqualTo(403);

        MockHttpServletRequest incorrect = request("POST", "/orders");
        incorrect.setSession(session);
        incorrect.addHeader(token.getHeaderName(), "incorrect-token");
        assertThat(perform(security, incorrect).getStatus()).isEqualTo(403);

        MockHttpServletRequest valid = request("POST", "/orders");
        valid.setSession(session);
        valid.addHeader(token.getHeaderName(), token.getToken());
        MockHttpServletResponse accepted = perform(security, valid);
        assertThat(accepted.getStatus()).isEqualTo(200);
        assertThat(accepted.getContentAsString()).isEqualTo("anonymousUser");
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private static MockHttpServletRequest authenticatedRequest(String path, String credentials) {
        MockHttpServletRequest request = request("GET", path);
        request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        return request;
    }

    private static MockHttpServletResponse perform(FilterChainProxy security, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        security.doFilter(request, response, (servletRequest, servletResponse) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(200);
            httpResponse.getWriter().write(authentication.getName());
        });
        return response;
    }
}
