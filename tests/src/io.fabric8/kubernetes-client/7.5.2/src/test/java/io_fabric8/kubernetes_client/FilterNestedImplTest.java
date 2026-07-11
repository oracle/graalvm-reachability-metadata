/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_client;

import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
public class FilterNestedImplTest {
    private KubernetesClient client;

    @Test
    void listsPodsWithRepeatedNegativeLabelAndFieldFilters() {
        PodList pods = client.pods()
                .inNamespace("test")
                .withNewFilter()
                .withoutLabel("app", "api")
                .withoutLabel("app", "worker")
                .withoutField("status.phase", "Succeeded")
                .withoutField("status.phase", "Failed")
                .endFilter()
                .list();

        assertThat(pods.getItems()).isEmpty();
    }
}
