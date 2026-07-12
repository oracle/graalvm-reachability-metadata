/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

@EnableKubernetesMockClient(crud = true)
public class ResourceHandlerImplTest {
    private static final String NAMESPACE = "resource-handler-test";
    private static final String POD_NAME = "visitor-edited-pod";

    private KubernetesClient client;

    @Test
    void editsResourceUsingPodBuilderVisitor() {
        Pod created = client.resource(new PodBuilder()
                .withNewMetadata()
                    .withNamespace(NAMESPACE)
                    .withName(POD_NAME)
                    .addToLabels("revision", "1")
                .endMetadata()
                .build()).create();

        assertThat(client.pods().inNamespace(NAMESPACE).list().getItems())
                .extracting(pod -> pod.getMetadata().getName())
                .contains(POD_NAME);
        Pod fetched = client.pods().inNamespace(NAMESPACE).withName(POD_NAME).get();
        assertThat(fetched.getMetadata().getLabels()).containsEntry("revision", "1");

        Pod updated = client.resource(created).edit(new Visitor<PodBuilder>() {
            @Override
            public Class<PodBuilder> getType() {
                return PodBuilder.class;
            }

            @Override
            public void visit(PodBuilder pod) {
                pod.editMetadata()
                        .addToLabels("revision", "2")
                        .endMetadata();
            }
        });

        assertThat(updated.getMetadata().getLabels()).containsEntry("revision", "2");
        assertThat(client.resource(updated).delete()).isNotEmpty();
        assertThat(client.pods().inNamespace(NAMESPACE).list().getItems()).isEmpty();
    }
}
