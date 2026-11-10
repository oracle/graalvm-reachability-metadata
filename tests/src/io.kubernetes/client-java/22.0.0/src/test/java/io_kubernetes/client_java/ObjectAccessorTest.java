/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ObjectAccessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectAccessorTest {
    @Test
    void readsResourceIdentityAndMetadata() throws Exception {
        V1ObjectMeta metadata = new V1ObjectMeta().name("sync-settings").namespace("apps").uid("config-17");
        V1ConfigMap config = new V1ConfigMap().metadata(metadata);

        assertThat(ObjectAccessor.objectMetadata(config)).isSameAs(metadata);
        assertThat(ObjectAccessor.objectMetadata(config).getUid()).isEqualTo("config-17");
        assertThat(ObjectAccessor.name(config)).isEqualTo("sync-settings");
        assertThat(ObjectAccessor.namespace(config)).isEqualTo("apps");
    }
}
