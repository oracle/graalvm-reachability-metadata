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
import com.c4_soft.springaddons.security.oidc.starter.ConfigurableClaimSetAuthoritiesConverter;
import com.c4_soft.springaddons.security.oidc.starter.properties.NotAConfiguredOpenidProviderException;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties.OpenidProviderProperties;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties.OpenidProviderProperties.SimpleAuthoritiesMappingProperties;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties.OpenidProviderProperties.SimpleAuthoritiesMappingProperties.Case;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigurableClaimSetAuthoritiesConverterTest {
    @Test
    void mapsNestedRolesAndDelimitedScopesUsingIssuerConfiguration() {
        SimpleAuthoritiesMappingProperties roles = mapping("$.realm_access.roles", "ROLE_", Case.UPPER);
        SimpleAuthoritiesMappingProperties scopes = mapping("$.scope", "SCOPE_", Case.LOWER);
        SimpleAuthoritiesMappingProperties absent = mapping("$.missing", "", Case.UNCHANGED);
        ConfigurableClaimSetAuthoritiesConverter converter = converter(roles, scopes, absent);
        Map<String, Object> claims = Map.of(
                "iss", "https://issuer.example",
                "realm_access", Map.of("roles", List.of("reader", " editor ", "", 42)),
                "scope", "OPENID,PROFILE email");

        assertThat(converter.convert(claims)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_READER", "ROLE_EDITOR", "SCOPE_openid", "SCOPE_profile", "SCOPE_email");
    }

    @Test
    void collectsRolesAcrossResourcesWithJsonPathWildcard() {
        ConfigurableClaimSetAuthoritiesConverter converter = converter(
                mapping("$.resource_access.*.roles", "", Case.UNCHANGED));
        Map<String, Object> claims = Map.of("iss", "https://issuer.example", "resource_access", Map.of(
                "catalog", Map.of("roles", List.of("read", "write")),
                "account", Map.of("roles", List.of("manage"))));

        assertThat(converter.convert(claims)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("read", "write", "manage");
    }

    @Test
    void refusesClaimsFromUnconfiguredIssuer() {
        ConfigurableClaimSetAuthoritiesConverter converter = converter(mapping("$.roles", "", Case.UNCHANGED));
        assertThrows(NotAConfiguredOpenidProviderException.class,
                () -> converter.convert(Map.of("iss", "https://untrusted.example", "roles", List.of("admin"))));
    }

    private static SimpleAuthoritiesMappingProperties mapping(String path, String prefix, Case letterCase) {
        SimpleAuthoritiesMappingProperties mapping = new SimpleAuthoritiesMappingProperties();
        mapping.setPath(path);
        mapping.setPrefix(prefix);
        mapping.setCaze(letterCase);
        return mapping;
    }

    private static ConfigurableClaimSetAuthoritiesConverter converter(SimpleAuthoritiesMappingProperties... mappings) {
        OpenidProviderProperties provider = new OpenidProviderProperties();
        provider.setIss(URI.create("https://issuer.example"));
        provider.setAuthorities(List.of(mappings));
        SpringAddonsOidcProperties properties = new SpringAddonsOidcProperties();
        properties.setOps(List.of(provider));
        return new ConfigurableClaimSetAuthoritiesConverter(new ByIssuerOpenidProviderPropertiesResolver(properties));
    }
}
