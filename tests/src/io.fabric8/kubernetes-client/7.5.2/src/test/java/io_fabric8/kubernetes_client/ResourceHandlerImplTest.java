/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_client;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true, https = false)
public class ResourceHandlerImplTest {
    private static final String NAMESPACE = "resource-handler";
    private static final String POD_NAME = "visitor-edited-pod";

    private KubernetesClient client;

    @Test
    void editsResourceWithVisitorThroughGenericResourceApi() {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(POD_NAME)
                .withNamespace(NAMESPACE)
                .addToLabels("state", "created")
                .endMetadata()
                .build();

        Pod created = client.pods().inNamespace(NAMESPACE).resource(pod).create();
        PodList pods = client.pods().inNamespace(NAMESPACE).list();
        assertThat(pods.getItems()).hasSize(1);
        assertThat(pods.getItems().get(0).getMetadata().getName()).isEqualTo(POD_NAME);

        Pod retrieved = client.pods().inNamespace(NAMESPACE).withName(POD_NAME).get();
        assertThat(retrieved).isNotNull();

        Pod edited = client.resource(retrieved)
                .inNamespace(NAMESPACE)
                .edit(new StateLabelVisitor());
        assertThat(edited.getMetadata().getLabels()).containsEntry("state", "edited");

        assertThat(client.pods().inNamespace(NAMESPACE).withName(POD_NAME).delete()).isNotEmpty();
        assertThat(client.pods().inNamespace(NAMESPACE).withName(POD_NAME).get()).isNull();
    }

    private static final class StateLabelVisitor implements Visitor<PodBuilder> {
        @Override
        public void visit(PodBuilder builder) {
            builder.editMetadata()
                    .addToLabels("state", "edited")
                    .endMetadata();
        }
    }
}
