/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1EndpointSubset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1EndpointSubsetTest {
    @Test
    void readsReadyAndPendingBackendsWithPorts() throws Exception {
        V1EndpointSubset subset = V1EndpointSubset.fromJson(ModelJson.ENDPOINT_SUBSET);
        assertThat(subset.getAddresses()).hasSize(1);
        assertThat(subset.getAddresses().get(0).getNodeName()).isEqualTo("worker-a");
        assertThat(subset.getNotReadyAddresses()).hasSize(1);
        assertThat(subset.getNotReadyAddresses().get(0).getIp()).isEqualTo("10.0.0.9");
        assertThat(subset.getPorts()).hasSize(1);
        assertThat(subset.getPorts().get(0).getPort()).isEqualTo(8080);
        ModelJson.assertJsonEquals(ModelJson.ENDPOINT_SUBSET, subset.toJson());
    }
}
