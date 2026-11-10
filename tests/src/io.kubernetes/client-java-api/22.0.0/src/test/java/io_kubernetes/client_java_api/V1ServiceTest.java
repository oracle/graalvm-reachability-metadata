/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ServiceTest {
    @Test
    void readsAndWritesServiceResponse() throws Exception {
        V1Service service = V1Service.fromJson(ModelJson.SERVICE);
        assertThat(service.getApiVersion()).isEqualTo("v1");
        assertThat(service.getKind()).isEqualTo("Service");
        assertThat(service.getMetadata().getNamespace()).isEqualTo("apps");
        assertThat(service.getSpec().getPorts().get(0).getTargetPort().getStrValue()).isEqualTo("http");
        assertThat(service.getStatus().getLoadBalancer().getIngress().get(0).getIp()).isEqualTo("192.0.2.10");
        ModelJson.assertJsonEquals(ModelJson.SERVICE, service.toJson());
    }
}
