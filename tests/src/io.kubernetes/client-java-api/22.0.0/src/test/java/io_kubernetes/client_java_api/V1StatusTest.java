/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1StatusTest {
    @Test
    void readsAndWritesApiValidationStatus() throws Exception {
        V1Status status = V1Status.fromJson(ModelJson.STATUS);
        assertThat(status.getApiVersion()).isEqualTo("v1");
        assertThat(status.getCode()).isEqualTo(422);
        assertThat(status.getDetails().getCauses().get(0).getField()).isEqualTo("spec.ports[0].port");
        assertThat(status.getKind()).isEqualTo("Status");
        assertThat(status.getMessage()).isEqualTo("Service is invalid");
        assertThat(status.getMetadata().getResourceVersion()).isEqualTo("42");
        assertThat(status.getReason()).isEqualTo("Invalid");
        assertThat(status.getStatus()).isEqualTo("Failure");
        ModelJson.assertJsonEquals(ModelJson.STATUS, status.toJson());
    }
}
