/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

@EnableKubernetesMockClient(crud = true)
public class FilterNestedImplTest {
    private static final String NAMESPACE = "filter-test";
    private static final String POD_NAME = "web";

    private KubernetesClient client;

    @Test
    void combinesRepeatedNegativeLabelAndFieldFiltersDuringPodLifecycle() {
        Pod created = client.pods().inNamespace(NAMESPACE).resource(new PodBuilder()
                .withNewMetadata()
                    .withName(POD_NAME)
                    .addToLabels("tier", "frontend")
                .endMetadata()
                .build()).create();

        assertThat(created.getMetadata().getName()).isEqualTo(POD_NAME);
        assertThat(client.pods().inNamespace(NAMESPACE).list().getItems())
                .extracting(pod -> pod.getMetadata().getName())
                .contains(POD_NAME);

        assertThat(client.pods().inNamespace(NAMESPACE).withNewFilter()
                .withoutLabel("tier", "batch")
                .withoutLabel("tier", "scheduled")
                .endFilter()
                .list()
                .getItems())
                .extracting(pod -> pod.getMetadata().getName())
                .contains(POD_NAME);
        assertThat(client.pods().inNamespace(NAMESPACE).withNewFilter()
                .withoutField("metadata.name", "retired")
                .withoutField("metadata.name", "obsolete")
                .endFilter()
                .list()
                .getItems())
                .extracting(pod -> pod.getMetadata().getName())
                .contains(POD_NAME);

        Pod fetched = client.pods().inNamespace(NAMESPACE).withName(POD_NAME).get();
        assertThat(fetched.getMetadata().getLabels()).containsEntry("tier", "frontend");

        Pod updated = client.pods().inNamespace(NAMESPACE).withName(POD_NAME).edit(pod -> new PodBuilder(pod)
                .editMetadata()
                    .addToLabels("revision", "2")
                .endMetadata()
                .build());
        assertThat(updated.getMetadata().getLabels()).containsEntry("revision", "2");

        assertThat(client.pods().inNamespace(NAMESPACE).withName(POD_NAME).delete()).isNotEmpty();
    }
}
