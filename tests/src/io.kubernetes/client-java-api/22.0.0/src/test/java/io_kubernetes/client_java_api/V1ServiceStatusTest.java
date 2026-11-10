/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1ServiceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ServiceStatusTest {
    @Test
    void readsAndWritesServiceConditionsAndIngress() throws Exception {
        V1ServiceStatus status = V1ServiceStatus.fromJson(ModelJson.SERVICE_STATUS);
        assertThat(status.getConditions()).hasSize(1);
        assertThat(status.getConditions().get(0).getReason()).isEqualTo("Allocated");
        assertThat(status.getLoadBalancer().getIngress()).hasSize(1);
        assertThat(status.getLoadBalancer().getIngress().get(0).getHostname()).isEqualTo("web.example.test");
        ModelJson.assertJsonEquals(ModelJson.SERVICE_STATUS, status.toJson());
    }
}
