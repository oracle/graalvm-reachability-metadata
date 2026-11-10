/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CoreV1EndpointPortTest {
    @Test
    void readsEndpointPortAndWritesItsWireFields() throws Exception {
        CoreV1EndpointPort port = CoreV1EndpointPort.fromJson(ModelJson.ENDPOINT_PORT);
        assertThat(port.getAppProtocol()).isEqualTo("http");
        assertThat(port.getName()).isEqualTo("web");
        assertThat(port.getPort()).isEqualTo(8080);
        assertThat(port.getProtocol()).isEqualTo("TCP");
        ModelJson.assertJsonEquals(ModelJson.ENDPOINT_PORT, port.toJson());
    }
}
