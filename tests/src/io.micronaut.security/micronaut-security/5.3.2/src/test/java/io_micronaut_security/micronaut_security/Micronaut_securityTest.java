/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.BasicAuthUtils;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.RedirectConfigurationProperties;
import io.micronaut.security.config.SecurityConfigurationProperties;
import io.micronaut.security.filters.SecurityFilter;
import io.micronaut.security.token.RolesFinder;
import io.micronaut.security.token.bearer.BearerTokenConfigurationProperties;
import io.micronaut.security.token.bearer.BearerTokenReader;
import io.micronaut.security.token.cookie.CookieTokenReader;
import io.micronaut.security.token.cookie.TokenCookieConfigurationProperties;
import io.micronaut.security.token.reader.DefaultTokenResolver;
import io.micronaut.security.token.reader.TokenReader;
import io.micronaut.security.token.render.AccessRefreshToken;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import io.micronaut.security.token.render.BearerTokenRenderer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration coverage for core authentication, configuration, and token APIs. */
@Timeout(55)
public class Micronaut_securityTest {
    @Test
    void applicationContextBindsSecurityConfigurationAndResolvesRequestTokens() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("micronaut.security.enabled", true);
        properties.put("micronaut.security.reject-not-found", false);
        properties.put("micronaut.security.authentication-provider-strategy", "all");
        properties.put("micronaut.security.intercept-url-map-prepend-pattern-with-context-path", false);
        properties.put("micronaut.security.intercept-url-map", List.of(Map.of(
                "pattern", "/admin/**",
                "access", List.of("ROLE_ADMIN"),
                "http-method", "GET")));
        properties.put("micronaut.security.redirect.login-success", "/signed-in");
        properties.put("micronaut.security.redirect.login-failure", "/sign-in");
        properties.put("micronaut.security.token.roles-name", "permissions");
        properties.put("micronaut.security.token.roles-separator", ",");
        properties.put("micronaut.security.token.bearer.header-name", "X-Api-Token");
        properties.put("micronaut.security.token.bearer.prefix", "Token");

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            SecurityConfigurationProperties security = context.getBean(SecurityConfigurationProperties.class);
            assertThat(security.isEnabled()).isTrue();
            assertThat(security.isRejectNotFound()).isFalse();
            assertThat(security.getAuthenticationProviderStrategy().toString()).isEqualTo("ALL");
            assertThat(security.isInterceptUrlMapPrependPatternWithContextPath()).isFalse();
            assertThat(security.getInterceptUrlMap()).hasSize(1);
            InterceptUrlMapPattern pattern = security.getInterceptUrlMap().get(0);
            assertThat(pattern.getPattern()).isEqualTo("/admin/**");
            assertThat(pattern.getAccess()).containsExactly("ROLE_ADMIN");
            assertThat((Object) pattern.getHttpMethod()).isEqualTo(HttpMethod.GET);

            RedirectConfigurationProperties redirects = context.getBean(RedirectConfigurationProperties.class);
            assertThat(redirects.getLoginSuccess()).isEqualTo("/signed-in");
            assertThat(redirects.getLoginFailure()).isEqualTo("/sign-in");

            RolesFinder rolesFinder = context.getBean(RolesFinder.class);
            assertThat(rolesFinder.resolveRoles(Map.of("permissions", "reader,editor")))
                    .containsExactly("reader", "editor");

            DefaultTokenResolver resolver = context.getBean(DefaultTokenResolver.class);
            HttpRequest<?> request = HttpRequest.GET("/admin/reports").header("X-Api-Token", "Token request-token");
            assertThat(resolver.resolveTokens(request)).containsExactly("request-token");

            SecurityFilter filter = context.getBean(SecurityFilter.class);
            assertThat(filter.getOrder()).isEqualTo(ServerFilterPhase.SECURITY.order());
        }
    }

    @Test
    void tokenReadersHonorHeaderAndCookieConfiguration() {
        BearerTokenConfigurationProperties bearerConfiguration = new BearerTokenConfigurationProperties();
        bearerConfiguration.setHeaderName("X-Security-Token");
        bearerConfiguration.setPrefix("Token");
        BearerTokenReader bearerReader = new BearerTokenReader(bearerConfiguration);

        TokenCookieConfigurationProperties cookieConfiguration = new TokenCookieConfigurationProperties();
        cookieConfiguration.setCookieName("SECURITY");
        CookieTokenReader cookieReader = new CookieTokenReader(cookieConfiguration);

        HttpRequest<?> request = HttpRequest.GET("/account")
                .header("X-Security-Token", "Token header-token")
                .cookie(Cookie.of("SECURITY", "cookie-token"));

        assertThat(bearerReader.findToken(request)).contains("header-token");
        assertThat(cookieReader.findToken(request)).contains("cookie-token");
        assertThat(new DefaultTokenResolver(List.<TokenReader<HttpRequest<?>>>of(bearerReader, cookieReader))
                        .resolveTokens(request))
                .containsExactly("header-token", "cookie-token");
        assertThat(bearerReader.findToken(HttpRequest.GET("/account").header("X-Security-Token", "Basic abc")))
                .isEmpty();
    }

    @Test
    void basicAuthenticationParsesUtf8CredentialsAndRejectsMalformedValues() {
        String encoded = Base64.getEncoder()
                .encodeToString("jose:pass:with:colons".getBytes(StandardCharsets.UTF_8));

        UsernamePasswordCredentials credentials = BasicAuthUtils.parseCredentials("Basic " + encoded).orElseThrow();

        assertThat(credentials.getIdentity()).isEqualTo("jose");
        assertThat(credentials.getSecret()).isEqualTo("pass:with:colons");
        assertThat(BasicAuthUtils.parseCredentials("Bearer " + encoded)).isEmpty();
        assertThat(BasicAuthUtils.parseCredentials("Basic not-base64!"))
                .isEmpty();
        assertThat(BasicAuthUtils.parseCredentials("Basic " + Base64.getEncoder().encodeToString(
                        "missing-delimiter".getBytes(StandardCharsets.UTF_8))))
                .isEmpty();
    }

    @Test
    void authenticationCopiesAndReplacesIdentityDataWithoutMutatingOriginal() {
        Authentication original = Authentication.build(
                "alice",
                List.of("ROLE_USER"),
                Map.of("tenant", "blue", "rolesKey", "permissions", "permissions", List.of("legacy")));

        Authentication enriched = original.withAttributes(Map.of("region", "west"), true)
                .withRoles(List.of("ROLE_EDITOR"), true)
                .withUsername("alice@example.test");

        assertThat(original.getName()).isEqualTo("alice");
        assertThat(original.getRoles()).containsExactly("ROLE_USER");
        assertThat(original.getAttributes()).containsEntry("tenant", "blue");
        assertThat(enriched.getName()).isEqualTo("alice@example.test");
        assertThat(enriched.getRoles()).containsExactly("ROLE_USER", "ROLE_EDITOR");
        assertThat(enriched.getAttributes())
                .containsEntry("tenant", "blue")
                .containsEntry("region", "west")
                .doesNotContainKeys("rolesKey", "permissions");
    }

    @Test
    void bearerTokenRendererProducesProtocolAndUserDetails() {
        Authentication authentication = Authentication.build(
                "service-user", List.of("catalog:read", "catalog:write"), Map.of("tenant", "inventory"));
        BearerTokenRenderer renderer = new BearerTokenRenderer();

        AccessRefreshToken anonymous = renderer.render(300, "access-token", "refresh-token");
        AccessRefreshToken rendered = renderer.render(authentication, 600, "user-access-token", null);

        assertThat(anonymous.getAccessToken()).isEqualTo("access-token");
        assertThat(anonymous.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(anonymous.getTokenType()).isEqualTo("Bearer");
        assertThat(anonymous.getExpiresIn()).isEqualTo(300);
        assertThat(rendered).isInstanceOf(BearerAccessRefreshToken.class);
        BearerAccessRefreshToken userToken = (BearerAccessRefreshToken) rendered;
        assertThat(userToken.getUsername()).isEqualTo("service-user");
        assertThat(userToken.getRoles()).containsExactly("catalog:read", "catalog:write");
        assertThat(userToken.getAccessToken()).isEqualTo("user-access-token");
        assertThat(userToken.getRefreshToken()).isNull();
        assertThat(userToken.getExpiresIn()).isEqualTo(600);
    }
}
