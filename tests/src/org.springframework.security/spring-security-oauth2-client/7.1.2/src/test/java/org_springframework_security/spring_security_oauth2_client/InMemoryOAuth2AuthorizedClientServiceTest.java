/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_client;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

import static org.assertj.core.api.Assertions.assertThat;

public class InMemoryOAuth2AuthorizedClientServiceTest {

    @Test
    void savesLoadsAndRemovesTokensSeparatelyForEachPrincipal() {
        ClientRegistration registration = OAuth2ClientTestSupport.registration("orders");
        InMemoryOAuth2AuthorizedClientService service = new InMemoryOAuth2AuthorizedClientService(
                new InMemoryClientRegistrationRepository(registration));
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "alice-access", issuedAt, issuedAt.plusSeconds(3600), Set.of("orders.read"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("alice-refresh", issuedAt);
        OAuth2AuthorizedClient alice = new OAuth2AuthorizedClient(registration, "alice", accessToken, refreshToken);
        OAuth2AuthorizedClient bob = new OAuth2AuthorizedClient(registration, "bob",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        "bob-access", issuedAt, issuedAt.plusSeconds(3600)));
        service.saveAuthorizedClient(alice, UsernamePasswordAuthenticationToken.unauthenticated("alice", "unused"));
        service.saveAuthorizedClient(bob, UsernamePasswordAuthenticationToken.unauthenticated("bob", "unused"));

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("orders", "alice");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getClientRegistration()).isSameAs(registration);
        assertThat(loaded.getPrincipalName()).isEqualTo("alice");
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("alice-access");
        assertThat(loaded.getAccessToken().getScopes()).containsExactly("orders.read");
        assertThat(loaded.getRefreshToken()).isSameAs(refreshToken);
        assertThat(service.<OAuth2AuthorizedClient>loadAuthorizedClient("orders", "unknown")).isNull();
        assertThat(service.<OAuth2AuthorizedClient>loadAuthorizedClient("missing", "alice")).isNull();

        service.removeAuthorizedClient("orders", "alice");

        assertThat(service.<OAuth2AuthorizedClient>loadAuthorizedClient("orders", "alice")).isNull();
        OAuth2AuthorizedClient remaining = service.loadAuthorizedClient("orders", "bob");
        assertThat(remaining).isNotNull();
        assertThat(remaining.getAccessToken().getTokenValue()).isEqualTo("bob-access");
    }
}
