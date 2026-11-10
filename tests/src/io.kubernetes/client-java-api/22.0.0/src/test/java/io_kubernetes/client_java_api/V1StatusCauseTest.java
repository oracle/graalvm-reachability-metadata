/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1StatusCause;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1StatusCauseTest {
    @Test
    void readsAndWritesFieldValidationCause() throws Exception {
        V1StatusCause cause = V1StatusCause.fromJson(ModelJson.STATUS_CAUSE);
        assertThat(cause.getField()).isEqualTo("spec.ports[0].port");
        assertThat(cause.getMessage()).isEqualTo("Must be a valid port");
        assertThat(cause.getReason()).isEqualTo("FieldValueInvalid");
        ModelJson.assertJsonEquals(ModelJson.STATUS_CAUSE, cause.toJson());
    }
}
