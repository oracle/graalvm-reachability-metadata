/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.Test;

public class HttpClientTest {
    @Test
    void initializesTheConfiguredTlsFactoryForHttpsLookup() throws Exception {
        try (PulsarClient client = PulsarClient.builder()
                .serviceUrl("https://localhost:8443")
                .allowTlsInsecureConnection(true)
                .build()) {
            assertThat(client).isNotNull();
        }
    }
}
