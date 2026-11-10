/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_c4_soft_springaddons.spring_addons_starter_oidc;

import java.util.HashMap;
import java.util.Map;

import com.c4_soft.springaddons.security.oidc.starter.PrincipalNameResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PrincipalNameResolverTest {
    @Test
    void resolvesNestedUsernameAndFallsBackToSubjectWhenAbsent() {
        Map<String, Object> claims = Map.of("sub", "subject-id", "profile", Map.of("username", "alice"));
        assertThat(PrincipalNameResolver.principalName(claims, "$.profile.username")).isEqualTo("alice");
        assertThat(PrincipalNameResolver.principalName(claims, "$.preferred_username")).isEqualTo("subject-id");
    }

    @Test
    void refusesMissingNameOrExplicitlyNullUsername() {
        assertThrows(InvalidBearerTokenException.class,
                () -> PrincipalNameResolver.principalName(Map.of("iss", "https://issuer.example"), "sub"));
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "subject-id");
        claims.put("preferred_username", null);
        assertThrows(InvalidBearerTokenException.class,
                () -> PrincipalNameResolver.principalName(claims, "preferred_username"));
    }
}
