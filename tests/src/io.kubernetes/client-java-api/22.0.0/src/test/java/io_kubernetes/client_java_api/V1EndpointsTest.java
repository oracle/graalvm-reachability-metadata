/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1Endpoints;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1EndpointsTest {
    @Test
    void readsAndWritesEndpointsResponse() throws Exception {
        V1Endpoints endpoints = V1Endpoints.fromJson(ModelJson.ENDPOINTS);
        assertThat(endpoints.getApiVersion()).isEqualTo("v1");
        assertThat(endpoints.getKind()).isEqualTo("Endpoints");
        assertThat(endpoints.getMetadata().getName()).isEqualTo("web");
        assertThat(endpoints.getSubsets()).hasSize(1);
        assertThat(endpoints.getSubsets().get(0).getAddresses().get(0).getIp()).isEqualTo("10.0.0.8");
        ModelJson.assertJsonEquals(ModelJson.ENDPOINTS, endpoints.toJson());
    }
}
