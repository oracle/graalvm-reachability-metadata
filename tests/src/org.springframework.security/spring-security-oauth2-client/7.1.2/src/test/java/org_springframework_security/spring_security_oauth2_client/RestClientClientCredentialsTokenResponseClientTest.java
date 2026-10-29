/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import org_springframework_security.spring_security_oauth2_client.OAuth2ClientTestSupport.TokenRequest;
import org_springframework_security.spring_security_oauth2_client.OAuth2ClientTestSupport.TokenServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
public class RestClientClientCredentialsTokenResponseClientTest {

    @Test
    void exchangesBasicAuthenticatedFormForAccessToken() throws Exception {
        try (TokenServer server = new TokenServer()) {
            ClientRegistration registration = ClientRegistration.withClientRegistration(
                    OAuth2ClientTestSupport.registration("orders")).tokenUri(server.tokenUri()).build();
            RestClientClientCredentialsTokenResponseClient client = OAuth2ClientTestSupport.tokenClient();

            OAuth2AccessTokenResponse response = client.getTokenResponse(new OAuth2ClientCredentialsGrantRequest(registration));

            assertThat(response.getAccessToken().getTokenValue()).isEqualTo("first-token");
            assertThat(response.getAccessToken().getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);
            assertThat(response.getAccessToken().getScopes()).containsExactly("orders.read");
            assertThat(response.getAccessToken().getExpiresAt()).isAfter(response.getAccessToken().getIssuedAt());
            assertThat(response.getRefreshToken()).isNull();
            TokenRequest request = server.request();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.contentType()).startsWith("application/x-www-form-urlencoded");
            assertThat(request.authorization()).isEqualTo("Basic " + Base64.getEncoder().encodeToString(
                    "orders-client:orders-secret".getBytes(StandardCharsets.UTF_8)));
            assertThat(request.parameters()).containsOnlyKeys("grant_type", "scope")
                    .containsEntry("grant_type", "client_credentials");
            assertThat(request.parameters().get("scope").split(" ")).containsExactlyInAnyOrder("orders.read", "orders.write");
        }
    }

    @Test
    void sendsPostClientCredentialsAndCustomParameters() throws Exception {
        try (TokenServer server = new TokenServer()) {
            server.respond(200, """
                    {"access_token":"post-token","token_type":"Bearer","expires_in":600,
                     "scope":"orders.read","tenant":"north"}
                    """);
            ClientRegistration registration = ClientRegistration.withClientRegistration(
                    OAuth2ClientTestSupport.registration("orders"))
                    .tokenUri(server.tokenUri())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .build();
            RestClientClientCredentialsTokenResponseClient client = OAuth2ClientTestSupport.tokenClient();
            client.setParametersCustomizer(parameters -> parameters.set("audience", "orders-api"));

            OAuth2AccessTokenResponse response = client.getTokenResponse(new OAuth2ClientCredentialsGrantRequest(registration));

            assertThat(response.getAccessToken().getTokenValue()).isEqualTo("post-token");
            assertThat(response.getAdditionalParameters()).containsEntry("tenant", "north");
            TokenRequest request = server.request();
            assertThat(request.authorization()).isNull();
            assertThat(request.parameters()).containsOnlyKeys("grant_type", "client_id", "client_secret", "scope", "audience")
                    .containsEntry("grant_type", "client_credentials")
                    .containsEntry("client_id", "orders-client")
                    .containsEntry("client_secret", "orders-secret")
                    .containsEntry("audience", "orders-api");
            assertThat(request.parameters().get("scope").split(" ")).containsExactlyInAnyOrder("orders.read", "orders.write");
        }
    }

    @Test
    void exposesOAuthErrorReturnedByTokenEndpoint() throws Exception {
        try (TokenServer server = new TokenServer()) {
            server.respond(400, """
                    {"error":"invalid_client","error_description":"Client credentials rejected"}
                    """);
            ClientRegistration registration = ClientRegistration.withClientRegistration(
                    OAuth2ClientTestSupport.registration("orders")).tokenUri(server.tokenUri()).build();
            RestClientClientCredentialsTokenResponseClient client = OAuth2ClientTestSupport.tokenClient();

            OAuth2AuthorizationException exception = assertThrows(OAuth2AuthorizationException.class,
                    () -> client.getTokenResponse(new OAuth2ClientCredentialsGrantRequest(registration)));

            assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_client");
            assertThat(exception.getError().getDescription()).isEqualTo("Client credentials rejected");
            assertThat(server.request().method()).isEqualTo("POST");
        }
    }
}
