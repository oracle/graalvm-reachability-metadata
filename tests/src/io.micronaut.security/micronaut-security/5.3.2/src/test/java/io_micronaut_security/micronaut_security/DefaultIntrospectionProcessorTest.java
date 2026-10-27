/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.endpoints.introspection.DefaultIntrospectionProcessor;
import io.micronaut.security.endpoints.introspection.IntrospectionRequest;
import io.micronaut.security.endpoints.introspection.IntrospectionResponse;
import io.micronaut.security.token.config.TokenConfigurationProperties;
import io.micronaut.security.token.validator.RefreshTokenValidator;
import io.micronaut.security.token.validator.TokenValidator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/** Functional coverage for OAuth 2.0 token introspection. */
@Timeout(55)
public class DefaultIntrospectionProcessorTest {
    @Test
    void introspectsValidatedTokensAndRejectsUnknownTokens() {
        Map<String, Object> attributes = Map.ofEntries(
                Map.entry("token_type", "Bearer"),
                Map.entry("scope", "catalog:read catalog:write"),
                Map.entry("client_id", "inventory-client"),
                Map.entry("username", "alice"),
                Map.entry("sub", "user-42"),
                Map.entry("aud", "inventory-api"),
                Map.entry("iss", "https://issuer.example"),
                Map.entry("jti", "token-17"),
                Map.entry("tenant", "blue"));
        Authentication authentication =
                Authentication.build("alice", List.of("ROLE_READER"), attributes);
        TokenValidator<HttpRequest<?>> tokenValidator = (token, request) ->
                "opaque-access-token".equals(token) ? Flux.just(authentication) : Flux.empty();
        RefreshTokenValidator refreshTokenValidator = token -> Optional.empty();
        DefaultIntrospectionProcessor<HttpRequest<?>> processor = new DefaultIntrospectionProcessor<>(
                List.of(tokenValidator), new TokenConfigurationProperties(), refreshTokenValidator);
        HttpRequest<?> request = HttpRequest.GET("/oauth/introspect");

        IntrospectionResponse active = Mono.from(processor.introspect(
                        new IntrospectionRequest("opaque-access-token", "access_token"), request))
                .block(Duration.ofSeconds(10));
        IntrospectionResponse inactive = Mono.from(processor.introspect(
                        new IntrospectionRequest("unknown-token", "access_token"), request))
                .block(Duration.ofSeconds(10));

        assertThat(active).isNotNull();
        assertThat(active.isActive()).isTrue();
        assertThat(active.getTokenType()).isEqualTo("Bearer");
        assertThat(active.getScope()).isEqualTo("catalog:read catalog:write");
        assertThat(active.getClientId()).isEqualTo("inventory-client");
        assertThat(active.getUsername()).isEqualTo("alice");
        assertThat(active.getSub()).isEqualTo("user-42");
        assertThat(active.getAud()).isEqualTo("inventory-api");
        assertThat(active.getIss()).isEqualTo("https://issuer.example");
        assertThat(active.getJti()).isEqualTo("token-17");
        assertThat(active.getExtensions())
                .containsEntry("tenant", "blue")
                .containsEntry("roles", List.of("ROLE_READER"))
                .doesNotContainKeys("token_type", "scope", "client_id", "username", "sub", "aud", "iss", "jti");

        assertThat(inactive).isNotNull();
        assertThat(inactive.isActive()).isFalse();
        assertThat(inactive.getExtensions()).isEmpty();
    }
}
