/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.javax.ws.rs.client.Client;
import org.apache.pulsar.shade.javax.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.Test;

public class ClientBuilderTest {
    @Test
    void discoversProviderAndBuildsClosableClient() {
        ClientBuilder builder = ClientBuilder.newBuilder();
        Client client = builder.build();
        try {
            assertThat(builder.getClass().getName()).contains("JerseyClientBuilder");
            assertThat(client.getConfiguration().isRegistered(Object.class)).isFalse();
        } finally {
            client.close();
        }
    }
}
