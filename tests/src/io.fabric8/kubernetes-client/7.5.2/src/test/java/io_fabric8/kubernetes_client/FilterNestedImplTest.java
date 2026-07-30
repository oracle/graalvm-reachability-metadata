/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_client;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true, https = false)
public class FilterNestedImplTest {
    private static final String NAMESPACE = "filters";
    private static final String POD_NAME = "filtered-pod";

    private KubernetesClient client;

    @Test
    void filtersResourcesWhilePerformingCrudOperations() {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(POD_NAME)
                .withNamespace(NAMESPACE)
                .addToLabels("app", "example")
                .endMetadata()
                .build();

        Pod created = client.pods().inNamespace(NAMESPACE).resource(pod).create();
        assertThat(created.getMetadata().getName()).isEqualTo(POD_NAME);

        PodList filteredPods = client.pods().inNamespace(NAMESPACE)
                .withNewFilter()
                .withoutLabel("environment", "production")
                .withoutLabel("environment", "staging")
                .withoutField("metadata.name", "another-pod")
                .withoutField("metadata.name", "a-different-pod")
                .endFilter()
                .list();
        assertThat(filteredPods.getItems()).extracting(item -> item.getMetadata().getName()).contains(POD_NAME);

        Pod retrieved = client.pods().inNamespace(NAMESPACE).withName(POD_NAME).get();
        assertThat(retrieved).isNotNull();

        Pod edited = client.pods().inNamespace(NAMESPACE).withName(POD_NAME).edit(current -> new PodBuilder(current)
                .editMetadata()
                .addToLabels("state", "edited")
                .endMetadata()
                .build());
        assertThat(edited.getMetadata().getLabels()).containsEntry("state", "edited");

        assertThat(client.pods().inNamespace(NAMESPACE).withName(POD_NAME).delete()).isNotEmpty();
        assertThat(client.pods().inNamespace(NAMESPACE).withName(POD_NAME).get()).isNull();
    }
}
