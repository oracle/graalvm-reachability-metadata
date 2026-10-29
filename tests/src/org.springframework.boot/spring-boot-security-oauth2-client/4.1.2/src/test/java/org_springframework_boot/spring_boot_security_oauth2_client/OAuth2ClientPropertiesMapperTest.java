/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_security_oauth2_client;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Provider;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Registration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ProviderDetails;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class OAuth2ClientPropertiesMapperTest {

    @Test
    void usesCommonProviderDefaultsForNamedAndAliasedRegistrations() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        properties.getRegistration().put("github", registration("source-control"));
        Registration workspace = registration("workspace-client");
        workspace.setProvider("google");
        properties.getRegistration().put("workspace", workspace);

        Map<String, ClientRegistration> clients = new OAuth2ClientPropertiesMapper(properties)
                .asClientRegistrations();

        assertThat(clients).containsOnlyKeys("github", "workspace");
        ClientRegistration github = clients.get("github");
        assertThat(github.getRegistrationId()).isEqualTo("github");
        assertThat(github.getClientId()).isEqualTo("source-control");
        assertThat(github.getClientSecret()).isEqualTo("client-secret");
        assertThat(github.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(github.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://github.com/login/oauth/authorize");
        assertThat(github.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("id");
        ClientRegistration google = clients.get("workspace");
        assertThat(google.getRegistrationId()).isEqualTo("workspace");
        assertThat(google.getClientId()).isEqualTo("workspace-client");
        assertThat(google.getScopes()).contains("openid", "profile", "email");
        assertThat(google.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
    }

    @Test
    void mapsExplicitProviderEndpointsAndRegistrationOptions() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        Registration portal = registration("portal-client");
        portal.setProvider("company");
        portal.setClientAuthenticationMethod("client_secret_post");
        portal.setAuthorizationGrantType("authorization_code");
        portal.setRedirectUri("https://portal.example/callback");
        portal.setScope(Set.of("openid", "profile"));
        portal.setClientName("Company login");
        properties.getRegistration().put("portal", portal);
        Provider provider = new Provider();
        provider.setAuthorizationUri("https://identity.example/authorize");
        provider.setTokenUri("https://identity.example/token");
        provider.setUserInfoUri("https://identity.example/userinfo");
        provider.setUserInfoAuthenticationMethod("form");
        provider.setUserNameAttribute("employee_id");
        provider.setJwkSetUri("https://identity.example/keys");
        properties.getProvider().put("company", provider);

        ClientRegistration client = new OAuth2ClientPropertiesMapper(properties)
                .asClientRegistrations().get("portal");

        assertThat(client.getRegistrationId()).isEqualTo("portal");
        assertThat(client.getClientId()).isEqualTo("portal-client");
        assertThat(client.getClientSecret()).isEqualTo("client-secret");
        assertThat(client.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(client.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(client.getRedirectUri()).isEqualTo("https://portal.example/callback");
        assertThat(client.getScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(client.getClientName()).isEqualTo("Company login");
        ProviderDetails details = client.getProviderDetails();
        assertThat(details.getAuthorizationUri()).isEqualTo("https://identity.example/authorize");
        assertThat(details.getTokenUri()).isEqualTo("https://identity.example/token");
        assertThat(details.getJwkSetUri()).isEqualTo("https://identity.example/keys");
        assertThat(details.getUserInfoEndpoint().getUri()).isEqualTo("https://identity.example/userinfo");
        assertThat(details.getUserInfoEndpoint().getAuthenticationMethod()).isEqualTo(AuthenticationMethod.FORM);
        assertThat(details.getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("employee_id");
    }

    @Test
    void mapsClientCredentialsUsingRegistrationIdAsProviderId() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        Registration service = registration("batch-client");
        service.setAuthorizationGrantType("client_credentials");
        service.setClientAuthenticationMethod("client_secret_basic");
        service.setScope(Set.of("reports.read"));
        properties.getRegistration().put("reports", service);
        Provider provider = new Provider();
        provider.setTokenUri("https://identity.example/token");
        properties.getProvider().put("reports", provider);

        ClientRegistration client = new OAuth2ClientPropertiesMapper(properties)
                .asClientRegistrations().get("reports");

        assertThat(client.getClientId()).isEqualTo("batch-client");
        assertThat(client.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(client.getProviderDetails().getTokenUri()).isEqualTo("https://identity.example/token");
        assertThat(client.getScopes()).containsExactly("reports.read");
    }

    @Test
    void overridesCommonProviderDefaultsWithExplicitSettings() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        Registration enterprise = registration("enterprise-client");
        enterprise.setProvider("github");
        enterprise.setScope(Set.of("read:org"));
        enterprise.setClientName("Enterprise source control");
        enterprise.setRedirectUri("https://portal.example/source-control");
        properties.getRegistration().put("enterprise", enterprise);
        Provider provider = new Provider();
        provider.setAuthorizationUri("https://git.example/login/oauth/authorize");
        provider.setTokenUri("https://git.example/login/oauth/access_token");
        provider.setUserInfoUri("https://git.example/api/user");
        properties.getProvider().put("github", provider);

        ClientRegistration client = new OAuth2ClientPropertiesMapper(properties)
                .asClientRegistrations().get("enterprise");

        assertThat(client.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://git.example/login/oauth/authorize");
        assertThat(client.getProviderDetails().getTokenUri())
                .isEqualTo("https://git.example/login/oauth/access_token");
        assertThat(client.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo("https://git.example/api/user");
        assertThat(client.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("id");
        assertThat(client.getScopes()).containsExactly("read:org");
        assertThat(client.getClientName()).isEqualTo("Enterprise source control");
        assertThat(client.getRedirectUri()).isEqualTo("https://portal.example/source-control");
    }

    @Test
    void rejectsUnknownOrUnspecifiedProviders() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        Registration portal = registration("portal-client");
        properties.getRegistration().put("portal", portal);
        OAuth2ClientPropertiesMapper mapper = new OAuth2ClientPropertiesMapper(properties);

        assertThatIllegalStateException().isThrownBy(mapper::asClientRegistrations)
                .withMessage("Provider ID must be specified for client registration 'portal'");
        portal.setProvider("unknown-provider");
        assertThatIllegalStateException().isThrownBy(mapper::asClientRegistrations)
                .withMessage("Unknown provider ID 'unknown-provider'");
    }

    @Test
    void mapsEmptyConfigurationToNoClients() {
        assertThat(new OAuth2ClientPropertiesMapper(new OAuth2ClientProperties()).asClientRegistrations()).isEmpty();
    }

    private static Registration registration(String clientId) {
        Registration registration = new Registration();
        registration.setClientId(clientId);
        registration.setClientSecret("client-secret");
        return registration;
    }
}
