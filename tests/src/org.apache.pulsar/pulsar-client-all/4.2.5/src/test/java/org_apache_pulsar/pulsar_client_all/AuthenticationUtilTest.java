/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.api.AuthenticationDataProvider;
import org.apache.pulsar.client.api.AuthenticationFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AuthenticationUtilTest {
    private static final String TOKEN_PLUGIN_CLASS =
            "org.apache.pulsar.client.impl.auth.AuthenticationToken";

    @Test
    void createsAndConfiguresTokenAuthenticationPlugin() throws Exception {
        String token = "spring-pulsar-token";

        try (Authentication authentication =
                AuthenticationFactory.create(TOKEN_PLUGIN_CLASS, token)) {
            authentication.start();
            AuthenticationDataProvider authenticationData = authentication.getAuthData();

            assertThat(authentication.getAuthMethodName()).isEqualTo("token");
            assertThat(authenticationData.hasDataFromCommand()).isTrue();
            assertThat(authenticationData.getCommandData()).isEqualTo(token);
        }
    }
}
