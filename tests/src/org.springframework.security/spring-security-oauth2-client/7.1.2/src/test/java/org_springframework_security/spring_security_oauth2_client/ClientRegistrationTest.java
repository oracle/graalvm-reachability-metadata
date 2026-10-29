/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_client;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ClientRegistrationTest {

    @Test
    void buildsPublicAuthorizationCodeClientAndCopiesProviderConfiguration() {
        ClientRegistration original = ClientRegistration.withRegistrationId("login")
                .clientId("browser-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://issuer.example.test/authorize")
                .tokenUri("https://issuer.example.test/token")
                .userInfoUri("https://issuer.example.test/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://issuer.example.test/jwks")
                .issuerUri("https://issuer.example.test")
                .providerConfigurationMetadata(Map.of("tenant", "orders"))
                .scope("openid", "profile")
                .clientName("Orders login")
                .build();
        ClientRegistration copy = ClientRegistration.withClientRegistration(original)
                .registrationId("secondary-login")
                .clientName("Secondary login")
                .build();

        assertThat(original.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        assertThat(original.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(original.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(copy.getRegistrationId()).isEqualTo("secondary-login");
        assertThat(copy.getClientName()).isEqualTo("Secondary login");
        assertThat(copy.getClientId()).isEqualTo("browser-client");
        assertThat(copy.getScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(copy.getRedirectUri()).isEqualTo(original.getRedirectUri());
        assertThat(copy.getProviderDetails().getAuthorizationUri()).isEqualTo("https://issuer.example.test/authorize");
        assertThat(copy.getProviderDetails().getTokenUri()).isEqualTo("https://issuer.example.test/token");
        assertThat(copy.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo("https://issuer.example.test/userinfo");
        assertThat(copy.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("sub");
        assertThat(copy.getProviderDetails().getJwkSetUri()).isEqualTo("https://issuer.example.test/jwks");
        assertThat(copy.getProviderDetails().getIssuerUri()).isEqualTo("https://issuer.example.test");
        assertThat(copy.getProviderDetails().getConfigurationMetadata()).containsEntry("tenant", "orders");
        assertThat(original.getRegistrationId()).isEqualTo("login");
        assertThat(original.getClientName()).isEqualTo("Orders login");
    }

    @Test
    void clientCredentialsDefaultsToBasicAuthenticationAndValidatesScopes() {
        ClientRegistration registration = OAuth2ClientTestSupport.registration("orders");

        assertThat(registration.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(registration.getClientSettings().isRequireProofKey()).isFalse();
        assertThat(registration.getClientSecret()).isEqualTo("orders-secret");
        assertThatIllegalArgumentException().isThrownBy(() -> ClientRegistration.withClientRegistration(registration)
                .scope("invalid scope").build());
    }
}
