/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_client;

import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.fabric8.mockwebserver.http.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class FilterNestedImplTest {
    @Test
    void repeatedNegativeLabelAndFieldFiltersAreSentToTheMockServer() throws Exception {
        Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
        KubernetesMockServer server = new KubernetesMockServer(
                new Context(Serialization.jsonMapper()),
                new MockWebServer(),
                responses,
                new KubernetesMixedDispatcher(responses),
                false);
        server.init();
        try (NamespacedKubernetesClient client = server.createClient()) {
            PodList pods = client.pods()
                    .inNamespace("test")
                    .withoutLabel("tier", "frontend")
                    .withoutLabel("tier", "backend")
                    .withoutField("metadata.name", "pod-a")
                    .withoutField("metadata.name", "pod-b")
                    .list();

            RecordedRequest request = server.takeRequest(10, TimeUnit.SECONDS);
            assertThat(pods.getItems()).isEmpty();
            assertThat(request).isNotNull();
            String decodedPath = URLDecoder.decode(request.getPath(), StandardCharsets.UTF_8);
            assertThat(decodedPath).startsWith("/api/v1/namespaces/test/pods?");
            assertThat(decodedPath).contains("labelSelector=tier!=frontend,tier!=backend");
            assertThat(decodedPath).contains("fieldSelector=metadata.name!=pod-a,metadata.name!=pod-b");
        } finally {
            server.destroy();
        }
    }
}
