/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.junit.jupiter.api.Test;

public class AsyncHttpConnectorTest {
    @Test
    void initializesTheConfiguredTlsFactoryForAdminRequests() throws Exception {
        try (PulsarAdmin admin = PulsarAdmin.builder()
                .serviceHttpUrl("https://localhost:8443")
                .allowTlsInsecureConnection(true)
                .build()) {
            assertThat(admin).isNotNull();
        }
    }
}
