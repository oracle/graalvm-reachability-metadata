/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_security_oauth2_client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Provider;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class OAuth2ClientPropertiesTest {

    @Test
    void bindsClientRegistrationAndProviderSettings() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        String registrationPrefix = "spring.security.oauth2.client.registration.portal.";
        source.put(registrationPrefix + "provider", "identity");
        source.put(registrationPrefix + "client-id", "portal-client");
        source.put(registrationPrefix + "client-secret", "portal-secret");
        source.put(registrationPrefix + "client-authentication-method", "client_secret_post");
        source.put(registrationPrefix + "authorization-grant-type", "authorization_code");
        source.put(registrationPrefix + "redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}");
        source.put(registrationPrefix + "scope", "openid,profile,email");
        source.put(registrationPrefix + "client-name", "Employee portal");
        String providerPrefix = "spring.security.oauth2.client.provider.identity.";
        source.put(providerPrefix + "authorization-uri", "https://identity.example/authorize");
        source.put(providerPrefix + "token-uri", "https://identity.example/token");
        source.put(providerPrefix + "user-info-uri", "https://identity.example/userinfo");
        source.put(providerPrefix + "user-info-authentication-method", "form");
        source.put(providerPrefix + "user-name-attribute", "sub");
        source.put(providerPrefix + "jwk-set-uri", "https://identity.example/keys");
        source.put(providerPrefix + "issuer-uri", "https://identity.example");

        OAuth2ClientProperties properties = new Binder(source)
                .bind("spring.security.oauth2.client", Bindable.of(OAuth2ClientProperties.class)).get();
        properties.afterPropertiesSet();

        assertThat(properties.getRegistration()).containsOnlyKeys("portal");
        Registration registration = properties.getRegistration().get("portal");
        assertThat(registration.getProvider()).isEqualTo("identity");
        assertThat(registration.getClientId()).isEqualTo("portal-client");
        assertThat(registration.getClientSecret()).isEqualTo("portal-secret");
        assertThat(registration.getClientAuthenticationMethod()).isEqualTo("client_secret_post");
        assertThat(registration.getAuthorizationGrantType()).isEqualTo("authorization_code");
        assertThat(registration.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
        assertThat(registration.getScope()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(registration.getClientName()).isEqualTo("Employee portal");
        assertThat(properties.getProvider()).containsOnlyKeys("identity");
        Provider provider = properties.getProvider().get("identity");
        assertThat(provider.getAuthorizationUri()).isEqualTo("https://identity.example/authorize");
        assertThat(provider.getTokenUri()).isEqualTo("https://identity.example/token");
        assertThat(provider.getUserInfoUri()).isEqualTo("https://identity.example/userinfo");
        assertThat(provider.getUserInfoAuthenticationMethod()).isEqualTo("form");
        assertThat(provider.getUserNameAttribute()).isEqualTo("sub");
        assertThat(provider.getJwkSetUri()).isEqualTo("https://identity.example/keys");
        assertThat(provider.getIssuerUri()).isEqualTo("https://identity.example");
    }

    @Test
    void rejectsMissingAndBlankClientIds() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        Registration registration = new Registration();
        properties.getRegistration().put("portal", registration);

        assertThatIllegalStateException().isThrownBy(properties::afterPropertiesSet)
                .withMessage("Client id of registration 'portal' must not be empty.");
        registration.setClientId("   ");
        assertThatIllegalStateException().isThrownBy(properties::validate)
                .withMessage("Client id of registration 'portal' must not be empty.");
        registration.setClientId("portal-client");
        properties.validate();
        assertThat(properties.getRegistration().get("portal").getClientId()).isEqualTo("portal-client");
    }

    @Test
    void acceptsConfigurationWithoutRegistrations() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        properties.afterPropertiesSet();
        assertThat(properties.getRegistration()).isEmpty();
        assertThat(properties.getProvider()).isEmpty();
    }
}
