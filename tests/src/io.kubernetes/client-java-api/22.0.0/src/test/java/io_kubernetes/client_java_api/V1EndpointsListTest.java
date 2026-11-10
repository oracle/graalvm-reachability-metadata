/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1EndpointsList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1EndpointsListTest {
    @Test
    void readsAndWritesEndpointListPage() throws Exception {
        V1EndpointsList list = V1EndpointsList.fromJson(ModelJson.ENDPOINTS_LIST);
        assertThat(list.getApiVersion()).isEqualTo("v1");
        assertThat(list.getKind()).isEqualTo("EndpointsList");
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("42");
        assertThat(list.getItems()).hasSize(1);
        assertThat(list.getItems().get(0).getSubsets().get(0).getPorts().get(0).getPort()).isEqualTo(8080);
        ModelJson.assertJsonEquals(ModelJson.ENDPOINTS_LIST, list.toJson());
    }
}
