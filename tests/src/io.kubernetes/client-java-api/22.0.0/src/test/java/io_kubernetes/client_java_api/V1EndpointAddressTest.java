/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1EndpointAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1EndpointAddressTest {
    @Test
    void readsAndWritesAddressIncludingNodeAndTarget() throws Exception {
        V1EndpointAddress address = V1EndpointAddress.fromJson(ModelJson.ENDPOINT_ADDRESS);
        assertThat(address.getHostname()).isEqualTo("web-0");
        assertThat(address.getIp()).isEqualTo("10.0.0.8");
        assertThat(address.getNodeName()).isEqualTo("worker-a");
        assertThat(address.getTargetRef().getUid()).isEqualTo("pod-uid");
        ModelJson.assertJsonEquals(ModelJson.ENDPOINT_ADDRESS, address.toJson());
    }
}
