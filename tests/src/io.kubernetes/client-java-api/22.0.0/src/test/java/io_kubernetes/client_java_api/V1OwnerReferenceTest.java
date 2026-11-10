/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1OwnerReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1OwnerReferenceTest {
    @Test
    void readsAndWritesControllingOwner() throws Exception {
        V1OwnerReference owner = V1OwnerReference.fromJson(ModelJson.OWNER_REFERENCE);
        assertThat(owner.getApiVersion()).isEqualTo("apps/v1");
        assertThat(owner.getBlockOwnerDeletion()).isTrue();
        assertThat(owner.getController()).isTrue();
        assertThat(owner.getKind()).isEqualTo("Deployment");
        assertThat(owner.getName()).isEqualTo("web");
        assertThat(owner.getUid()).isEqualTo("deployment-uid");
        ModelJson.assertJsonEquals(ModelJson.OWNER_REFERENCE, owner.toJson());
    }
}
