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
import org.apache.pulsar.client.impl.auth.AuthenticationBasic;
import org.apache.pulsar.client.impl.auth.AuthenticationToken;
import org.junit.jupiter.api.Test;

public class AuthenticationUtilTest {

    @Test
    void createsConfiguredAuthenticationPluginsFromBothParameterForms() throws Exception {
        try (Authentication encoded = AuthenticationUtil.create(
                        AuthenticationToken.class.getName(), "token:encoded-secret");
                Authentication mapped = AuthenticationUtil.create(
                        AuthenticationBasic.class.getName(),
                        Map.of("userId", "client", "password", "mapped-secret"))) {
            encoded.start();
            mapped.start();

            assertThat(encoded.getAuthMethodName()).isEqualTo("token");
            assertThat(encoded.getAuthData().getCommandData()).isEqualTo("encoded-secret");
            assertThat(mapped.getAuthMethodName()).isEqualTo("basic");
            assertThat(mapped.getAuthData().getCommandData()).isEqualTo("client:mapped-secret");
        }
    }
}
