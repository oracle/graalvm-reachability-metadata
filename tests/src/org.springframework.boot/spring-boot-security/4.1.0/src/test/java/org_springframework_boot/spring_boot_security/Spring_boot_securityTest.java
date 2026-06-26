/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_security;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.StaticResourceLocation;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.web.servlet.ApplicationContextRequestMatcher;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.boot.web.servlet.DispatcherType;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_securityTest {

    private final WebApplicationContextRunner userDetailsContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
                    UserDetailsServiceAutoConfiguration.class));

    private final WebApplicationContextRunner servletSecurityContextRunner = new WebApplicationContextRunner()
            .withBean(DispatcherServletPath.class, () -> () -> "/")
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
                    UserDetailsServiceAutoConfiguration.class, SecurityFilterAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class));

    private final WebApplicationContextRunner requestMatcherContextRunner = new WebApplicationContextRunner()
            .withBean(DispatcherServletPath.class, () -> () -> "/");

    @Test
    void securityPropertiesBindDefaultUserAndCreateInMemoryUserDetailsService() {
        this.userDetailsContextRunner
                .withPropertyValues("spring.security.user.name=alice", "spring.security.user.password=s3cr3t",
                        "spring.security.user.roles=ADMIN,OPS")
                .run((context) -> {
                    assertThat(context).hasSingleBean(SecurityProperties.class);
                    assertThat(context).hasSingleBean(DefaultAuthenticationEventPublisher.class);
                    assertThat(context).hasSingleBean(InMemoryUserDetailsManager.class);
                    assertThat(context).hasSingleBean(UserDetailsService.class);

                    SecurityProperties.User properties = context.getBean(SecurityProperties.class).getUser();
                    assertThat(properties.getName()).isEqualTo("alice");
                    assertThat(properties.getPassword()).isEqualTo("s3cr3t");
                    assertThat(properties.getRoles()).containsExactly("ADMIN", "OPS");
                    assertThat(properties.isPasswordGenerated()).isFalse();

                    UserDetails user = context.getBean(UserDetailsService.class).loadUserByUsername("alice");
                    assertThat(user.getUsername()).isEqualTo("alice");
                    assertThat(user.getPassword()).isEqualTo("{noop}s3cr3t");
                    assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                            .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_OPS");
                });
    }

    @Test
    void generatedDefaultPasswordIsUsableWhenNoPasswordPropertyIsConfigured() {
        this.userDetailsContextRunner.run((context) -> {
            SecurityProperties.User properties = context.getBean(SecurityProperties.class).getUser();
            assertThat(properties.getName()).isEqualTo("user");
            assertThat(properties.getPassword()).isNotBlank();
            assertThat(properties.isPasswordGenerated()).isTrue();

            UserDetails user = context.getBean(InMemoryUserDetailsManager.class).loadUserByUsername("user");
            assertThat(user.getPassword()).isEqualTo("{noop}" + properties.getPassword());
            assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).isEmpty();
        });
    }

    @Test
    void customPasswordEncoderKeepsConfiguredPasswordEncoded() {
        this.userDetailsContextRunner.withUserConfiguration(PasswordEncoderConfiguration.class)
                .withPropertyValues("spring.security.user.name=encoded", "spring.security.user.password=encoded:s3cr3t")
                .run((context) -> {
                    assertThat(context).hasSingleBean(PasswordEncoder.class);
                    assertThat(context).hasSingleBean(InMemoryUserDetailsManager.class);

                    PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
                    UserDetails user = context.getBean(InMemoryUserDetailsManager.class).loadUserByUsername("encoded");
                    assertThat(user.getPassword()).isEqualTo("encoded:s3cr3t");
                    assertThat(passwordEncoder.matches("s3cr3t", user.getPassword())).isTrue();
                });
    }

    @Test
    void customUserDetailsServiceMakesDefaultInMemoryUserBackOff() {
        UserDetails customUser = User.withUsername("carol").password("{noop}custom").roles("SUPPORT").build();

        this.userDetailsContextRunner.withBean(UserDetailsService.class, () -> (username) -> customUser)
                .withPropertyValues("spring.security.user.name=ignored", "spring.security.user.password=ignored")
                .run((context) -> {
                    assertThat(context).hasSingleBean(UserDetailsService.class);
                    assertThat(context).doesNotHaveBean(InMemoryUserDetailsManager.class);

                    UserDetails user = context.getBean(UserDetailsService.class).loadUserByUsername("carol");
                    assertThat(user.getUsername()).isEqualTo("carol");
                    assertThat(user.getPassword()).isEqualTo("{noop}custom");
                    assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                            .containsExactly("ROLE_SUPPORT");
                });
    }

    @Test
    void servletSecurityAutoConfigurationCreatesDefaultFilterChainAndRegistration() {
        this.servletSecurityContextRunner
                .withPropertyValues("spring.security.user.name=web", "spring.security.user.password=secret",
                        "spring.security.filter.order=-77",
                        "spring.security.filter.dispatcher-types=request,error")
                .run((context) -> {
                    assertThat(context).hasSingleBean(SecurityFilterProperties.class);
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasSingleBean(DelegatingFilterProxyRegistrationBean.class);
                    assertThat(context).hasSingleBean(PathPatternRequestMatcher.Builder.class);

                    SecurityFilterChain filterChain = context.getBean(SecurityFilterChain.class);
                    assertThat(filterChain.getFilters()).anySatisfy((filter) -> assertThat(filter)
                            .isInstanceOf(UsernamePasswordAuthenticationFilter.class));
                    assertThat(filterChain.getFilters()).anySatisfy((filter) -> assertThat(filter)
                            .isInstanceOf(BasicAuthenticationFilter.class));
                    assertThat(filterChain.getFilters()).anySatisfy((filter) -> assertThat(filter)
                            .isInstanceOf(AuthorizationFilter.class));

                    DelegatingFilterProxyRegistrationBean registration = context
                            .getBean(DelegatingFilterProxyRegistrationBean.class);
                    assertThat(registration.getOrder()).isEqualTo(-77);
                    assertThat(registration.getFilterName()).isEqualTo("springSecurityFilterChain");
                    assertThat(registration.determineDispatcherTypes())
                            .isEqualTo(EnumSet.of(jakarta.servlet.DispatcherType.REQUEST,
                                    jakarta.servlet.DispatcherType.ERROR));
                });
    }

    @Test
    void customSecurityFilterChainMakesServletDefaultFilterChainBackOff() {
        this.servletSecurityContextRunner.withUserConfiguration(CustomSecurityFilterChainConfiguration.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
                    assertThat(chain.getFilters()).noneSatisfy((filter) -> assertThat(filter)
                            .isInstanceOf(UsernamePasswordAuthenticationFilter.class));
                });
    }

    @Test
    void servletPathRequestMatchesCommonStaticResourcesAndHonorsExclusions() {
        this.requestMatcherContextRunner.run((context) -> {
            context.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,
                    context);
            RequestMatcher commonLocations = PathRequest.toStaticResources().atCommonLocations();
            RequestMatcher withoutCss = PathRequest.toStaticResources().atCommonLocations()
                    .excluding(StaticResourceLocation.CSS);

            assertThat(commonLocations.matches(request(context, "/css/site.css"))).isTrue();
            assertThat(commonLocations.matches(request(context, "/webjars/jquery/jquery.js"))).isTrue();
            assertThat(commonLocations.matches(request(context, "/favicon.ico"))).isTrue();
            assertThat(commonLocations.matches(request(context, "/api/orders"))).isFalse();

            assertThat(withoutCss.matches(request(context, "/css/site.css"))).isFalse();
            assertThat(withoutCss.matches(request(context, "/js/app.js"))).isTrue();
        });
    }

    @Test
    void servletPathRequestMatchesSelectedStaticResourceLocations() {
        this.requestMatcherContextRunner.run((context) -> {
            context.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,
                    context);
            RequestMatcher imagesAndFonts = PathRequest.toStaticResources()
                    .at(Set.of(StaticResourceLocation.IMAGES, StaticResourceLocation.FONTS));

            assertThat(StaticResourceLocation.IMAGES.getPatterns()).contains("/images/**");
            assertThat(StaticResourceLocation.FONTS.getPatterns()).contains("/fonts/**");
            assertThat(imagesAndFonts.matches(request(context, "/images/logo.png"))).isTrue();
            assertThat(imagesAndFonts.matches(request(context, "/fonts/app.woff2"))).isTrue();
            assertThat(imagesAndFonts.matches(request(context, "/css/site.css"))).isFalse();
        });
    }

    @Test
    void securityFilterPropertiesExposeDefaultOrderAndDispatcherTypes() {
        SecurityFilterProperties properties = new SecurityFilterProperties();

        assertThat(properties.getOrder()).isEqualTo(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
        assertThat(properties.getDispatcherTypes()).containsExactlyInAnyOrder(DispatcherType.REQUEST,
                DispatcherType.ERROR, DispatcherType.ASYNC, DispatcherType.FORWARD, DispatcherType.INCLUDE);

        properties.setOrder(SecurityFilterProperties.BASIC_AUTH_ORDER);
        properties.setDispatcherTypes(Set.of(DispatcherType.REQUEST, DispatcherType.ERROR));

        assertThat(properties.getOrder()).isEqualTo(SecurityFilterProperties.BASIC_AUTH_ORDER);
        assertThat(properties.getDispatcherTypes()).containsExactlyInAnyOrder(DispatcherType.REQUEST,
                DispatcherType.ERROR);
    }

    @Test
    void applicationContextRequestMatcherUsesCurrentWebApplicationContext() {
        this.requestMatcherContextRunner.run((context) -> {
            context.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,
                    context);
            ContextIdRequestMatcher matcher = new ContextIdRequestMatcher();

            MockHttpServletRequest matchingRequest = request(context, "/secure");
            matchingRequest.setParameter("contextId", context.getId());
            assertThat(matcher.matches(matchingRequest)).isTrue();
            assertThat(matcher.getInitializedCount()).isOne();

            MockHttpServletRequest nonMatchingRequest = request(context, "/secure");
            nonMatchingRequest.setParameter("contextId", "other");
            assertThat(matcher.matches(nonMatchingRequest)).isFalse();
            assertThat(matcher.getInitializedCount()).isOne();
        });
    }

    private static MockHttpServletRequest request(WebApplicationContext context, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(context.getServletContext(), "GET", path);
        request.setServletPath(path);
        return request;
    }

    private static final class ContextIdRequestMatcher extends ApplicationContextRequestMatcher<WebApplicationContext> {

        private final AtomicInteger initializedCount = new AtomicInteger();

        private ContextIdRequestMatcher() {
            super(WebApplicationContext.class);
        }

        int getInitializedCount() {
            return this.initializedCount.get();
        }

        @Override
        protected void initialized(Supplier<WebApplicationContext> context) {
            this.initializedCount.incrementAndGet();
        }

        @Override
        protected boolean matches(HttpServletRequest request, Supplier<WebApplicationContext> context) {
            return context.get().getId().equals(request.getParameter("contextId"));
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class PasswordEncoderConfiguration {

        @Bean
        PasswordEncoder prefixPasswordEncoder() {
            return new PrefixPasswordEncoder();
        }

    }

    private static final class PrefixPasswordEncoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSecurityFilterChainConfiguration {

        @Bean
        SecurityFilterChain customSecurityFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll());
            return http.build();
        }

    }

}
