/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1ManagedFieldsEntry;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ManagedFieldsEntryTest {
    @Test
    void readsAndWritesFieldOwnership() throws Exception {
        V1ManagedFieldsEntry entry = V1ManagedFieldsEntry.fromJson(ModelJson.MANAGED_FIELDS);
        assertThat(entry.getApiVersion()).isEqualTo("v1");
        assertThat(entry.getFieldsType()).isEqualTo("FieldsV1");
        assertThat(entry.getFieldsV1()).isEqualTo(Map.of("f:spec", Map.of("f:ports", Map.of())));
        assertThat(entry.getManager()).isEqualTo("service-controller");
        assertThat(entry.getOperation()).isEqualTo("Update");
        assertThat(entry.getSubresource()).isEqualTo("status");
        assertThat(entry.getTime()).isEqualTo(OffsetDateTime.parse("2024-03-04T05:06:07Z"));
        ModelJson.assertJsonEquals(ModelJson.MANAGED_FIELDS, entry.toJson());
    }
}
