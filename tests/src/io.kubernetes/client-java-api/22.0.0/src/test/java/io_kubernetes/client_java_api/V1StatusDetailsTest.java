/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1StatusDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1StatusDetailsTest {
    @Test
    void readsAndWritesStatusResourceAndCauses() throws Exception {
        V1StatusDetails details = V1StatusDetails.fromJson(ModelJson.STATUS_DETAILS);
        assertThat(details.getCauses()).hasSize(1);
        assertThat(details.getCauses().get(0).getReason()).isEqualTo("FieldValueInvalid");
        assertThat(details.getGroup()).isEmpty();
        assertThat(details.getKind()).isEqualTo("services");
        assertThat(details.getName()).isEqualTo("web");
        assertThat(details.getRetryAfterSeconds()).isEqualTo(10);
        assertThat(details.getUid()).isEqualTo("service-uid");
        ModelJson.assertJsonEquals(ModelJson.STATUS_DETAILS, details.toJson());
    }
}
