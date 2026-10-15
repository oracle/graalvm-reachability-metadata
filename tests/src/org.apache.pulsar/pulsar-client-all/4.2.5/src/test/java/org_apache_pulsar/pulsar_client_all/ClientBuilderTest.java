/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.shade.javax.ws.rs.client.Client;
import org.apache.pulsar.shade.javax.ws.rs.client.ClientBuilder;
import org.apache.pulsar.shade.javax.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ClientBuilderTest {
    @Test
    void buildsAndConfiguresJerseyClientTarget() {
        Client client = ClientBuilder.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        try {
            WebTarget target = client.target("http://localhost:8080")
                    .path("admin/v2/clusters")
                    .queryParam("authoritative", true);
            URI uri = target.getUri();

            assertThat(uri.getScheme()).isEqualTo("http");
            assertThat(uri.getPath()).isEqualTo("/admin/v2/clusters");
            assertThat(uri.getQuery()).isEqualTo("authoritative=true");
            assertThat(client.getConfiguration().getRuntimeType().name()).isEqualTo("CLIENT");
        } finally {
            client.close();
        }
    }
}
