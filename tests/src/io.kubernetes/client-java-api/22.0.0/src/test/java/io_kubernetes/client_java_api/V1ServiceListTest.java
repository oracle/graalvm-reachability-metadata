/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1ServiceList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ServiceListTest {
    @Test
    void readsAndWritesServiceListPage() throws Exception {
        V1ServiceList list = V1ServiceList.fromJson(ModelJson.SERVICE_LIST);
        assertThat(list.getApiVersion()).isEqualTo("v1");
        assertThat(list.getKind()).isEqualTo("ServiceList");
        assertThat(list.getMetadata().getContinue()).isEqualTo("next-page");
        assertThat(list.getItems()).hasSize(1);
        assertThat(list.getItems().get(0).getMetadata().getName()).isEqualTo("web");
        assertThat(list.getItems().get(0).getSpec().getClusterIP()).isEqualTo("10.96.0.10");
        ModelJson.assertJsonEquals(ModelJson.SERVICE_LIST, list.toJson());
    }
}
