/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_c4_soft_springaddons.spring_addons_starter_oidc;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.c4_soft.springaddons.security.oidc.starter.AdditionalParamsAuthorizationRequestCustomizer;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class AdditionalParamsAuthorizationRequestCustomizerTest {
    @Test
    void addsRegistrationSpecificParametersToTheAuthorizationUri() {
        SpringAddonsOidcClientProperties properties = new SpringAddonsOidcClientProperties();
        properties.setAuthorizationParams(Map.of("browser", Map.of(
                "audience", List.of("catalog", "account"), "prompt", List.of("consent")),
                "other", Map.of("prompt", List.of("login"))));
        AdditionalParamsAuthorizationRequestCustomizer customizer = new AdditionalParamsAuthorizationRequestCustomizer(
                properties.getExtraAuthorizationParameters("browser"));
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://issuer.example/authorize")
                .clientId("frontend")
                .redirectUri("https://client.example/callback")
                .state("login-state")
                .additionalParameters(Map.of("existing", "preserved"));
        customizer.accept(builder);
        OAuth2AuthorizationRequest request = builder.build();

        assertThat(request.getAdditionalParameters()).containsEntry("audience", "catalog,account")
                .containsEntry("prompt", "consent").containsEntry("existing", "preserved");
        assertThat(UriComponentsBuilder.fromUri(URI.create(request.getAuthorizationRequestUri()))
                .build().getQueryParams()).containsEntry("audience", List.of("catalog,account"))
                .containsEntry("state", List.of("login-state"));
        assertThat(properties.getExtraAuthorizationParameters("unconfigured")).isEmpty();
    }
}
