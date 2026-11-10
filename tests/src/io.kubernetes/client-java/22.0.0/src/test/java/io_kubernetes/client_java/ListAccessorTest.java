/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java;

import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.ListAccessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ListAccessorTest {
    @Test
    void readsItemsAndPaginationMetadata() throws Exception {
        V1Pod first = new V1Pod().metadata(new V1ObjectMeta().name("worker-a"));
        V1Pod second = new V1Pod().metadata(new V1ObjectMeta().name("worker-b"));
        V1ListMeta metadata = new V1ListMeta().resourceVersion("341")._continue("next-page");
        V1PodList pods = new V1PodList().metadata(metadata).addItemsItem(first).addItemsItem(second);

        assertThat(ListAccessor.<V1Pod>getItems(pods)).containsExactly(first, second);
        assertThat(ListAccessor.listMetadata(pods)).isSameAs(metadata);
        assertThat(ListAccessor.listMetadata(pods).getContinue()).isEqualTo("next-page");
        assertThat(ListAccessor.listMetadata(pods).getResourceVersion()).isEqualTo("341");
    }
}
