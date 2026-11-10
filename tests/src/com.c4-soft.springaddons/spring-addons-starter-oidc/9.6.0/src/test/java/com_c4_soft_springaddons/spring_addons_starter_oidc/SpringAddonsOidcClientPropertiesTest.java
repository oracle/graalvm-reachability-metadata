/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_c4_soft_springaddons.spring_addons_starter_oidc;

import java.net.URI;
import java.util.Optional;

import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcClientProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringAddonsOidcClientPropertiesTest {
    @Test
    void buildsRedirectsAndAcceptsOnlyLocalOrSameOriginDestinations() {
        SpringAddonsOidcClientProperties properties = new SpringAddonsOidcClientProperties();
        properties.setClientUri(Optional.of(URI.create("https://client.example:8443/bff")));
        properties.setPostLoginRedirectPath(Optional.of("/ui/home"));
        properties.setPostLogoutRedirectPath(Optional.of("/ui/signed-out"));

        assertThat(properties.getPostLoginRedirectUri()).isEqualTo(URI.create("https://client.example:8443/ui/home"));
        assertThat(properties.getPostLogoutRedirectUri()).isEqualTo(URI.create("https://client.example:8443/ui/signed-out"));
        assertThat(SpringAddonsOidcClientProperties.isAllowedRedirectionUri(
                "/ui/home?tab=account", properties.getPostLoginAllowedUriPatterns())).isTrue();
        assertThat(SpringAddonsOidcClientProperties.isAllowedRedirectionUri(
                "https://client.example:8443/ui/home", properties.getPostLoginAllowedUriPatterns())).isTrue();
        assertThat(SpringAddonsOidcClientProperties.isAllowedRedirectionUri(
                "https://untrusted.example/ui", properties.getPostLoginAllowedUriPatterns())).isFalse();
        assertThat(SpringAddonsOidcClientProperties.isAllowedRedirectionUri(
                "//untrusted.example/ui", properties.getPostLogoutAllowedUriPatterns())).isFalse();
        assertThat(SpringAddonsOidcClientProperties.isAllowedRedirectionUri(
                "https://client.example:8443.attacker.example/ui", properties.getPostLogoutAllowedUriPatterns())).isFalse();
    }
}
