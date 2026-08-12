/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_security_oauth2_resource_server;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_security_oauth2_resource_serverTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OAuth2ResourceServerAutoConfiguration.class));

    @Test
    void jwtPropertiesRetainResourceServerConfigurationAndReadPublicKey() throws Exception {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        OAuth2ResourceServerProperties.Jwt jwt = properties.getJwt();
        String publicKey = "-----BEGIN PUBLIC KEY-----\nkey-material\n-----END PUBLIC KEY-----";

        jwt.setIssuerUri("https://issuer.example.test");
        jwt.setJwkSetUri("https://issuer.example.test/keys");
        jwt.setJwsAlgorithms(List.of("RS256", "ES256"));
        jwt.setAudiences(List.of("orders", "invoices"));
        jwt.setAuthorityPrefix("ROLE_");
        jwt.setAuthoritiesClaimDelimiter(",");
        jwt.setAuthoritiesClaimName("roles");
        jwt.setAuthoritiesExpressions(List.of("realm_access.roles", "groups"));
        jwt.setPrincipalClaimName("preferred_username");
        jwt.setPublicKeyLocation(new ByteArrayResource(publicKey.getBytes(StandardCharsets.UTF_8)));

        assertThat(jwt.getIssuerUri()).isEqualTo("https://issuer.example.test");
        assertThat(jwt.getJwkSetUri()).isEqualTo("https://issuer.example.test/keys");
        assertThat(jwt.getJwsAlgorithms()).containsExactly("RS256", "ES256");
        assertThat(jwt.getAudiences()).containsExactly("orders", "invoices");
        assertThat(jwt.getAuthorityPrefix()).isEqualTo("ROLE_");
        assertThat(jwt.getAuthoritiesClaimDelimiter()).isEqualTo(",");
        assertThat(jwt.getAuthoritiesClaimName()).isEqualTo("roles");
        assertThat(jwt.getAuthoritiesClaimExpressions()).containsExactly("realm_access.roles", "groups");
        assertThat(jwt.getPrincipalClaimName()).isEqualTo("preferred_username");
        assertThat(jwt.readPublicKey()).isEqualTo(publicKey);
    }

    @Test
    void autoConfigurationBindsJwtAndOpaqueTokenProperties() {
        this.contextRunner
                .withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.example.test/keys",
                        "spring.security.oauth2.resourceserver.jwt.audiences=orders,invoices",
                        "spring.security.oauth2.resourceserver.jwt.authority-prefix=ROLE_",
                        "spring.security.oauth2.resourceserver.jwt.authorities-claim-name=roles",
                        "spring.security.oauth2.resourceserver.jwt.principal-claim-name=preferred_username",
                        "spring.security.oauth2.resourceserver.opaquetoken.introspection-uri=https://issuer.example.test/introspect",
                        "spring.security.oauth2.resourceserver.opaquetoken.client-id=resource-server",
                        "spring.security.oauth2.resourceserver.opaquetoken.client-secret=secret")
                .run((context) -> {
                    assertThat(context).hasSingleBean(OAuth2ResourceServerProperties.class);

                    OAuth2ResourceServerProperties properties = context.getBean(OAuth2ResourceServerProperties.class);
                    assertThat(properties.getJwt().getIssuerUri()).isEqualTo("https://issuer.example.test");
                    assertThat(properties.getJwt().getJwkSetUri()).isEqualTo("https://issuer.example.test/keys");
                    assertThat(properties.getJwt().getAudiences()).containsExactly("orders", "invoices");
                    assertThat(properties.getJwt().getAuthorityPrefix()).isEqualTo("ROLE_");
                    assertThat(properties.getJwt().getAuthoritiesClaimName()).isEqualTo("roles");
                    assertThat(properties.getJwt().getPrincipalClaimName()).isEqualTo("preferred_username");
                    assertThat(properties.getOpaquetoken().getIntrospectionUri())
                            .isEqualTo("https://issuer.example.test/introspect");
                    assertThat(properties.getOpaquetoken().getClientId()).isEqualTo("resource-server");
                    assertThat(properties.getOpaquetoken().getClientSecret()).isEqualTo("secret");
                });
    }

}
