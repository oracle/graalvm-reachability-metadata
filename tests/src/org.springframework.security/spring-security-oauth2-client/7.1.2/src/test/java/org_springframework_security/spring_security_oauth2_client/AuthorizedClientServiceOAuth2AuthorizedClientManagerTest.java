/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import org_springframework_security.spring_security_oauth2_client.OAuth2ClientTestSupport.TokenServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
public class AuthorizedClientServiceOAuth2AuthorizedClientManagerTest {

    @Test
    void persistsReusesAndRenewsClientCredentialsTokens() throws Exception {
        try (TokenServer server = new TokenServer()) {
            ClientRegistration registration = ClientRegistration.withClientRegistration(
                    OAuth2ClientTestSupport.registration("orders")).tokenUri(server.tokenUri()).build();
            InMemoryClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(registration);
            InMemoryOAuth2AuthorizedClientService service = new InMemoryOAuth2AuthorizedClientService(repository);
            ClientCredentialsOAuth2AuthorizedClientProvider provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
            provider.setAccessTokenResponseClient(OAuth2ClientTestSupport.tokenClient());
            provider.setClockSkew(Duration.ZERO);
            provider.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
            AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                    new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, service);
            manager.setAuthorizedClientProvider(provider);
            OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId("orders")
                    .principal("orders-worker").build();

            OAuth2AuthorizedClient first = manager.authorize(request);

            assertThat(first).isNotNull();
            assertThat(first.getPrincipalName()).isEqualTo("orders-worker");
            assertThat(first.getAccessToken().getTokenValue()).isEqualTo("first-token");
            assertThat(first.getRefreshToken()).isNull();
            assertThat(server.request().body()).contains("grant_type=client_credentials");
            OAuth2AuthorizedClient stored = service.loadAuthorizedClient("orders", "orders-worker");
            assertThat(stored).isNotNull();
            assertThat(stored.getAccessToken()).isSameAs(first.getAccessToken());

            OAuth2AuthorizedClient cached = manager.authorize(request);

            assertThat(cached).isNotNull();
            assertThat(cached.getAccessToken()).isSameAs(first.getAccessToken());
            assertThat(server.pendingRequests()).isZero();
            provider.setClock(Clock.fixed(first.getAccessToken().getExpiresAt().plusSeconds(1), ZoneOffset.UTC));
            server.respond(200, """
                    {"access_token":"renewed-token","token_type":"Bearer","expires_in":3600,"scope":"orders.read"}
                    """);

            OAuth2AuthorizedClient renewed = manager.authorize(request);

            assertThat(renewed).isNotNull();
            assertThat(renewed.getAccessToken().getTokenValue()).isEqualTo("renewed-token");
            assertThat(server.request().body()).contains("grant_type=client_credentials");
            OAuth2AuthorizedClient updated = service.loadAuthorizedClient("orders", "orders-worker");
            assertThat(updated).isNotNull();
            assertThat(updated.getAccessToken()).isSameAs(renewed.getAccessToken());
        }
    }

    @Test
    void removesPreviouslyAuthorizedClientWhenRenewalReturnsInvalidGrant() throws Exception {
        try (TokenServer server = new TokenServer()) {
            ClientRegistration registration = ClientRegistration.withClientRegistration(
                    OAuth2ClientTestSupport.registration("orders")).tokenUri(server.tokenUri()).build();
            InMemoryClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(registration);
            InMemoryOAuth2AuthorizedClientService service = new InMemoryOAuth2AuthorizedClientService(repository);
            ClientCredentialsOAuth2AuthorizedClientProvider provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
            provider.setAccessTokenResponseClient(OAuth2ClientTestSupport.tokenClient());
            AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                    new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, service);
            manager.setAuthorizedClientProvider(provider);
            OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId("orders")
                    .principal("orders-worker").build();
            OAuth2AuthorizedClient first = manager.authorize(request);
            assertThat(first).isNotNull();
            assertThat(server.request().method()).isEqualTo("POST");
            provider.setClock(Clock.fixed(first.getAccessToken().getExpiresAt().plusSeconds(1), ZoneOffset.UTC));
            server.respond(400, """
                    {"error":"invalid_grant","error_description":"Authorization revoked"}
                    """);

            ClientAuthorizationException exception = assertThrows(ClientAuthorizationException.class,
                    () -> manager.authorize(request));

            assertThat(exception.getClientRegistrationId()).isEqualTo("orders");
            assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_grant");
            assertThat(server.request().method()).isEqualTo("POST");
            assertThat(service.<OAuth2AuthorizedClient>loadAuthorizedClient("orders", "orders-worker")).isNull();
        }
    }
}
