/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_resource_server;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtBearerTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_security_oauth2_resource_serverTest {

    @Test
    void protectedResourceMetadataExposesStandardAndCustomClaims() throws Exception {
        OAuth2ProtectedResourceMetadata metadata = OAuth2ProtectedResourceMetadata.builder()
                .resource("https://api.example.test")
                .authorizationServer("https://issuer.example.test")
                .authorizationServers(addToList("https://backup.example.test"))
                .scope("orders.read")
                .scopes(addToList("orders.write"))
                .bearerMethod("header")
                .bearerMethods(addToList("body"))
                .resourceName("Orders API")
                .tlsClientCertificateBoundAccessTokens(true)
                .claim("tenant", "acme")
                .claims(addClaim("audience", List.of("orders")))
                .build();

        assertThat(metadata.getResource()).isEqualTo(URI.create("https://api.example.test").toURL());
        assertThat(metadata.getAuthorizationServers()).containsExactly(
                URI.create("https://issuer.example.test").toURL(),
                URI.create("https://backup.example.test").toURL());
        assertThat(metadata.getScopes()).containsExactly("orders.read", "orders.write");
        assertThat(metadata.getBearerMethodsSupported()).containsExactly("header", "body");
        assertThat(metadata.getResourceName()).isEqualTo("Orders API");
        assertThat(metadata.isTlsClientCertificateBoundAccessTokens()).isTrue();
        assertThat(metadata.getClaims()).containsEntry("tenant", "acme")
                .containsEntry("audience", List.of("orders"));
    }

    @Test
    void jwtAuthoritiesConvertersReadConfiguredClaimsAndRemoveDuplicates() {
        Jwt jwt = jwt(Map.of("scope", "read write", "permissions", "export|admin"));
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtGrantedAuthoritiesConverter permissions = new JwtGrantedAuthoritiesConverter();
        permissions.setAuthoritiesClaimName("permissions");
        permissions.setAuthoritiesClaimDelimiter("\\|");
        permissions.setAuthorityPrefix("PERMISSION_");

        DelegatingJwtGrantedAuthoritiesConverter converter =
                new DelegatingJwtGrantedAuthoritiesConverter(scopes, scopes, permissions);
        List<String> authorities = authorityNames(converter.convert(jwt));

        assertThat(authorities).containsExactly("SCOPE_read", "SCOPE_write", "PERMISSION_export", "PERMISSION_admin");
    }

    @Test
    void jwtAuthenticationConverterUsesConfiguredPrincipalAndAuthorities() {
        Jwt jwt = jwt(Map.of("scope", "catalog.read", "preferred_username", "marie"));
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");

        Authentication authentication = converter.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("marie");
        assertThat(authorityNames(authentication.getAuthorities()))
                .containsExactlyInAnyOrder("FACTOR_BEARER", "SCOPE_catalog.read");
        assertThat(authentication.getCredentials()).isEqualTo(jwt);
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void jwtBearerTokenAuthenticationConverterUsesCustomPrincipalConverter() {
        Jwt jwt = jwt(Map.of("scope", "orders.read", "sub", "token-subject"));
        DefaultOAuth2AuthenticatedPrincipal customPrincipal = new DefaultOAuth2AuthenticatedPrincipal(
                "service-account", Map.of("client_id", "orders-client"),
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
        JwtBearerTokenAuthenticationConverter converter = new JwtBearerTokenAuthenticationConverter();
        converter.setJwtPrincipalConverter(new Converter<>() {
            @Override
            public OAuth2AuthenticatedPrincipal convert(Jwt source) {
                return customPrincipal;
            }
        });

        BearerTokenAuthentication authentication = (BearerTokenAuthentication) converter.convert(jwt);

        assertThat(authentication.getPrincipal()).isSameAs(customPrincipal);
        assertThat(authentication.getName()).isEqualTo("service-account");
        assertThat(authorityNames(authentication.getAuthorities())).containsExactly("SCOPE_orders.read");
        assertThat(authentication.getToken().getTokenValue()).isEqualTo("token-value");
    }

    @Test
    void bearerAuthenticationRetainsPrincipalTokenAttributesAndAuthorities() {
        Instant issuedAt = Instant.parse("2025-01-01T00:00:00Z");
        DefaultOAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
                "sam", Map.of("sub", "sam", "department", "sales"),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-token", issuedAt, issuedAt.plusSeconds(300));
        BearerTokenAuthentication authentication = new BearerTokenAuthentication(
                principal, accessToken, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        BearerTokenAuthenticationToken unauthenticated = new BearerTokenAuthenticationToken("access-token");

        assertThat(authentication.getName()).isEqualTo("sam");
        assertThat(authentication.getToken()).isSameAs(accessToken);
        assertThat(authentication.getTokenAttributes()).containsEntry("department", "sales");
        assertThat(authorityNames(authentication.getAuthorities())).containsExactly("ROLE_USER");
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(unauthenticated.getToken()).isEqualTo("access-token");
        assertThat(unauthenticated.getCredentials()).isEqualTo("access-token");
        assertThat(unauthenticated.getPrincipal()).isEqualTo("access-token");
        assertThat(unauthenticated.isAuthenticated()).isFalse();
    }

    @Test
    void introspectionPrincipalAndBearerErrorsExposeOAuth2Values() {
        OAuth2IntrospectionAuthenticatedPrincipal principal = new OAuth2IntrospectionAuthenticatedPrincipal(
                "client-a", Map.of("sub", "subject-a", "active", true),
                List.of(new SimpleGrantedAuthority("SCOPE_profile")));
        BearerTokenError invalidRequest = BearerTokenErrors.invalidRequest("Malformed header");
        BearerTokenError invalidToken = BearerTokenErrors.invalidToken("Expired token");
        BearerTokenError insufficientScope = BearerTokenErrors.insufficientScope("Need orders.read", "orders.read");

        assertThat(principal.getName()).isEqualTo("client-a");
        assertThat(principal.getClaims()).containsEntry("active", true);
        assertThat(authorityNames(principal.getAuthorities())).containsExactly("SCOPE_profile");
        assertThat(invalidRequest.getErrorCode()).isEqualTo("invalid_request");
        assertThat(invalidRequest.getHttpStatus().value()).isEqualTo(400);
        assertThat(invalidToken.getErrorCode()).isEqualTo("invalid_token");
        assertThat(invalidToken.getHttpStatus().value()).isEqualTo(401);
        assertThat(insufficientScope.getErrorCode()).isEqualTo("insufficient_scope");
        assertThat(insufficientScope.getHttpStatus().value()).isEqualTo(403);
        assertThat(insufficientScope.getScope()).isEqualTo("orders.read");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .issuedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2025-01-01T00:05:00Z"))
                .claims(putAllClaims(claims))
                .build();
    }

    private List<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        List<String> names = new ArrayList<>();
        for (GrantedAuthority authority : authorities) {
            names.add(authority.getAuthority());
        }
        return names;
    }

    private Consumer<List<String>> addToList(String value) {
        return new Consumer<>() {
            @Override
            public void accept(List<String> values) {
                values.add(value);
            }
        };
    }

    private Consumer<Map<String, Object>> addClaim(String name, Object value) {
        return new Consumer<>() {
            @Override
            public void accept(Map<String, Object> claims) {
                claims.put(name, value);
            }
        };
    }

    private Consumer<Map<String, Object>> putAllClaims(Map<String, Object> source) {
        return new Consumer<>() {
            @Override
            public void accept(Map<String, Object> claims) {
                claims.putAll(source);
            }
        };
    }
}
