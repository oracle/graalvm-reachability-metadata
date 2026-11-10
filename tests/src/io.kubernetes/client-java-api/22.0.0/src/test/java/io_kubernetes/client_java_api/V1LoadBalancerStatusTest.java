/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1LoadBalancerStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1LoadBalancerStatusTest {
    @Test
    void readsAndWritesIngressCollection() throws Exception {
        V1LoadBalancerStatus status = V1LoadBalancerStatus.fromJson(ModelJson.LOAD_BALANCER);
        assertThat(status.getIngress()).hasSize(1);
        assertThat(status.getIngress().get(0).getIp()).isEqualTo("192.0.2.10");
        ModelJson.assertJsonEquals(ModelJson.LOAD_BALANCER, status.toJson());
    }
}
