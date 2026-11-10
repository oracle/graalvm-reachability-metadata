/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1ServicePort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ServicePortTest {
    @Test
    void readsAndWritesNamedTargetPort() throws Exception {
        V1ServicePort port = V1ServicePort.fromJson(ModelJson.SERVICE_PORT);
        assertThat(port.getAppProtocol()).isEqualTo("http");
        assertThat(port.getName()).isEqualTo("web");
        assertThat(port.getNodePort()).isEqualTo(30080);
        assertThat(port.getPort()).isEqualTo(80);
        assertThat(port.getProtocol()).isEqualTo("TCP");
        assertThat(port.getTargetPort().isInteger()).isFalse();
        assertThat(port.getTargetPort().getStrValue()).isEqualTo("http");
        ModelJson.assertJsonEquals(ModelJson.SERVICE_PORT, port.toJson());
    }

    @Test
    void readsAndWritesNumericTargetPort() throws Exception {
        String payload = """
                {"port":80,"targetPort":8080}
                """;
        V1ServicePort port = V1ServicePort.fromJson(payload);
        assertThat(port.getTargetPort().isInteger()).isTrue();
        assertThat(port.getTargetPort().getIntValue()).isEqualTo(8080);
        ModelJson.assertJsonEquals(payload, port.toJson());
    }
}
