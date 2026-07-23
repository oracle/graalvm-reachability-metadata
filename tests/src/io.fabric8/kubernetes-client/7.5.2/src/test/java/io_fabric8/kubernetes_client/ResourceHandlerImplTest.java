/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_client;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceHandlerImplTest {
    @Test
    void visitorEditUsesInferredResourceBuilderForPod() throws Exception {
        Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
        KubernetesMockServer server = new KubernetesMockServer(
                new Context(Serialization.jsonMapper()),
                new MockWebServer(),
                responses,
                new KubernetesMixedDispatcher(responses),
                false);
        server.init();
        try (NamespacedKubernetesClient client = server.createClient()) {
            Pod pod = new PodBuilder()
                    .withNewMetadata()
                    .withName("visitor-edit")
                    .withNamespace("test")
                    .addToLabels("app", "demo")
                    .endMetadata()
                    .withNewSpec()
                    .addNewContainer()
                    .withName("main")
                    .withImage("busybox")
                    .endContainer()
                    .endSpec()
                    .build();
            client.pods().inNamespace("test").resource(pod).create();

            Pod edited = client.pods()
                    .inNamespace("test")
                    .withName("visitor-edit")
                    .edit(new TypedVisitor<ObjectMetaBuilder>() {
                        @Override
                        public void visit(ObjectMetaBuilder metadata) {
                            metadata.addToLabels("edited", "true");
                        }
                    });

            assertThat(edited.getMetadata().getLabels())
                    .containsEntry("app", "demo")
                    .containsEntry("edited", "true");
        } finally {
            server.destroy();
        }
    }
}
