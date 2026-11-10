/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1PortStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1PortStatusTest {
    @Test
    void readsAndWritesPortAllocationStatus() throws Exception {
        V1PortStatus status = V1PortStatus.fromJson(ModelJson.PORT_STATUS);
        assertThat(status.getError()).isEqualTo("PortAllocationPending");
        assertThat(status.getPort()).isEqualTo(8080);
        assertThat(status.getProtocol()).isEqualTo("TCP");
        ModelJson.assertJsonEquals(ModelJson.PORT_STATUS, status.toJson());
    }
}
