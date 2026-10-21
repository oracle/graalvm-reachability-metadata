/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.impl.AuthenticationUtil;
import org.apache.pulsar.client.impl.auth.AuthenticationToken;
import org.junit.jupiter.api.Test;

public class AuthenticationUtilTest {
    @Test
    void createsAuthenticationPluginFromEncodedAndMappedParameters() throws Exception {
        Authentication encoded = AuthenticationUtil.create(
                AuthenticationToken.class.getName(), "{\"token\":\"encoded-secret\"}");
        Authentication mapped = AuthenticationUtil.create(
                AuthenticationToken.class.getName(), Map.of("token", "mapped-secret"));

        assertThat(encoded).isInstanceOf(AuthenticationToken.class);
        assertThat(encoded.getAuthMethodName()).isEqualTo("token");
        assertThat(encoded.getAuthData().getCommandData()).isEqualTo("encoded-secret");
        assertThat(mapped).isInstanceOf(AuthenticationToken.class);
        assertThat(mapped.getAuthMethodName()).isEqualTo("token");

        encoded.close();
        mapped.close();
    }
}
