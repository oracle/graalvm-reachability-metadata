/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1LoadBalancerIngressTest {
    @Test
    void readsAndWritesLoadBalancerAddressAndPorts() throws Exception {
        V1LoadBalancerIngress ingress = V1LoadBalancerIngress.fromJson(ModelJson.INGRESS);
        assertThat(ingress.getHostname()).isEqualTo("web.example.test");
        assertThat(ingress.getIp()).isEqualTo("192.0.2.10");
        assertThat(ingress.getIpMode()).isEqualTo("VIP");
        assertThat(ingress.getPorts()).hasSize(1);
        assertThat(ingress.getPorts().get(0).getError()).isEqualTo("PortAllocationPending");
        ModelJson.assertJsonEquals(ModelJson.INGRESS, ingress.toJson());
    }
}
