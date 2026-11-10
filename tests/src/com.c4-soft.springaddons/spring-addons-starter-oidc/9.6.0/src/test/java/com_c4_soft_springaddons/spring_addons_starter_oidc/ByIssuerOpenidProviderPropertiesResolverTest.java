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

import com.c4_soft.springaddons.security.oidc.starter.ByIssuerOpenidProviderPropertiesResolver;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties.OpenidProviderProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ByIssuerOpenidProviderPropertiesResolverTest {
    @Test
    void selectsExactIssuerAndDoesNotTrustOtherIssuers() {
        OpenidProviderProperties first = new OpenidProviderProperties();
        first.setIss(URI.create("https://issuer.example/first"));
        OpenidProviderProperties second = new OpenidProviderProperties();
        second.setIss(URI.create("https://issuer.example/second"));
        SpringAddonsOidcProperties properties = new SpringAddonsOidcProperties();
        properties.setOps(List.of(first, second));
        ByIssuerOpenidProviderPropertiesResolver resolver = new ByIssuerOpenidProviderPropertiesResolver(properties);

        assertThat(resolver.resolve(Map.of("iss", second.getIss().toString()))).containsSame(second);
        assertThat(resolver.resolve(Map.of("iss", first.getIss()))).containsSame(first);
        assertThat(resolver.resolve(Map.of("iss", "https://issuer.example/first/"))).isEmpty();
        assertThat(resolver.resolve(Map.of("iss", "https://untrusted.example"))).isEmpty();
        assertThat(resolver.resolve(Map.of("sub", "alice"))).isEmpty();
    }
}
