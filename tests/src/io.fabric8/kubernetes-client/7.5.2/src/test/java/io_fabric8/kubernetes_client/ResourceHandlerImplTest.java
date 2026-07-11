/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_client;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
public class ResourceHandlerImplTest {
    private KubernetesClient client;

    @Test
    void editsResourceUsingVisitor() {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("visitor-pod")
                .withNamespace("test")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("application")
                .withImage("example.test/application:1")
                .endContainer()
                .endSpec()
                .build();
        client.resource(pod).create();

        Pod edited = client.resource(pod).edit(new ContainerImageVisitor());

        assertThat(edited.getSpec().getContainers()).hasSize(1);
        assertThat(edited.getSpec().getContainers().get(0).getImage())
                .isEqualTo("example.test/application:2");
    }

    private static final class ContainerImageVisitor implements Visitor<ContainerBuilder> {
        @Override
        public Class<ContainerBuilder> getType() {
            return ContainerBuilder.class;
        }

        @Override
        public void visit(ContainerBuilder container) {
            container.withImage("example.test/application:2");
        }
    }
}
