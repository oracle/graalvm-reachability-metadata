/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

public class OidcUserInfoTest {
    @Test
    void userInfoBuilderExposesStandardAndCustomClaims() {
        OidcUserInfo userInfo = OidcUserInfo.builder().subject("subject-7").name("Ada Lovelace")
                .email("ada@example.test").emailVerified(true)
                .claims(claims -> claims.put("tenant", "example")).build();

        assertThat(userInfo.getSubject()).isEqualTo("subject-7");
        assertThat(userInfo.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(userInfo.getEmail()).isEqualTo("ada@example.test");
        assertThat(userInfo.getEmailVerified()).isTrue();
        assertThat(userInfo.getClaims()).containsEntry("tenant", "example");
    }
}
