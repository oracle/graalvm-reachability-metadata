/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.workflows.WorkflowStepRepresentation;
import org.keycloak.util.JsonSerialization;

public class MultivaluedHashMapValueSerializerTest {
    @Test
    void serializesWorkflowStepConfigWithoutDuplicatingDeclaredStepProperties() throws IOException {
        WorkflowStepRepresentation step = WorkflowStepRepresentation.create()
                .of("send-email")
                .after("10")
                .withConfig("enabled", "true")
                .withConfig("groups", "admins", "users")
                .build();

        String json = JsonSerialization.writeValueAsString(step);
        JsonNode node = JsonSerialization.readValue(json, JsonNode.class);

        assertThat(node.path("uses").asText()).isEqualTo("send-email");
        assertThat(node.path("after").asText()).isEqualTo("10");
        assertThat(node.path("with").has("after")).isFalse();
        assertThat(node.path("with").path("enabled").asBoolean()).isTrue();
        assertThat(node.path("with").path("groups").size()).isEqualTo(2);
        assertThat(node.path("with").path("groups").get(0).asText()).isEqualTo("admins");
        assertThat(node.path("with").path("groups").get(1).asText()).isEqualTo("users");
    }
}
