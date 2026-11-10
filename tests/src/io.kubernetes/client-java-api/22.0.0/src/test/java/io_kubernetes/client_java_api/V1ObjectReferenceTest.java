/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1ObjectReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ObjectReferenceTest {
    @Test
    void readsAndWritesPodReference() throws Exception {
        V1ObjectReference reference = V1ObjectReference.fromJson(ModelJson.OBJECT_REFERENCE);
        assertThat(reference.getApiVersion()).isEqualTo("v1");
        assertThat(reference.getFieldPath()).isEqualTo("spec.containers{web}");
        assertThat(reference.getKind()).isEqualTo("Pod");
        assertThat(reference.getName()).isEqualTo("web-0");
        assertThat(reference.getNamespace()).isEqualTo("apps");
        assertThat(reference.getResourceVersion()).isEqualTo("42");
        assertThat(reference.getUid()).isEqualTo("pod-uid");
        ModelJson.assertJsonEquals(ModelJson.OBJECT_REFERENCE, reference.toJson());
    }
}
