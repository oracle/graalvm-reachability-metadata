/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ObjectMetaTest {
    @Test
    // Kubernetes annotations are resource metadata, not Java reflection annotations.
    @SuppressWarnings("checkstyle:annotationAccess")
    void readsAndWritesResourceIdentityAndLifecycleMetadata() throws Exception {
        V1ObjectMeta metadata = V1ObjectMeta.fromJson(ModelJson.OBJECT_META);
        assertThat(metadata.getAnnotations()).containsEntry("sync.example.test/enabled", "true");
        assertThat(metadata.getCreationTimestamp()).isEqualTo(OffsetDateTime.parse("2024-03-04T05:06:07Z"));
        assertThat(metadata.getDeletionGracePeriodSeconds()).isEqualTo(30L);
        assertThat(metadata.getDeletionTimestamp()).isEqualTo(OffsetDateTime.parse("2024-03-05T05:06:07Z"));
        assertThat(metadata.getFinalizers()).containsExactly("sync.example.test/cleanup");
        assertThat(metadata.getGenerateName()).isEqualTo("web-");
        assertThat(metadata.getGeneration()).isEqualTo(3L);
        assertThat(metadata.getLabels()).containsEntry("app", "web");
        assertThat(metadata.getName()).isEqualTo("web");
        assertThat(metadata.getNamespace()).isEqualTo("apps");
        assertThat(metadata.getOwnerReferences()).hasSize(1);
        assertThat(metadata.getOwnerReferences().get(0).getUid()).isEqualTo("deployment-uid");
        assertThat(metadata.getResourceVersion()).isEqualTo("42");
        assertThat(metadata.getSelfLink()).isEqualTo("/api/v1/namespaces/apps/services/web");
        assertThat(metadata.getUid()).isEqualTo("service-uid");
        ModelJson.assertJsonEquals(ModelJson.OBJECT_META, metadata.toJson());
    }
}
