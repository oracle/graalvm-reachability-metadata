/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.workflows.WorkflowStepRepresentation;
import org.keycloak.util.JsonSerialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.keycloak.representations.workflows.WorkflowConstants.CONFIG_AFTER;
import static org.keycloak.representations.workflows.WorkflowConstants.CONFIG_USES;
import static org.keycloak.representations.workflows.WorkflowConstants.CONFIG_WITH;

public class MultivaluedHashMapValueSerializerTest {
    @Test
    void serializesWorkflowStepConfigurationWithoutDuplicatingTopLevelProperties() throws Exception {
        WorkflowStepRepresentation step = WorkflowStepRepresentation.create()
                .of("notify-user")
                .withConfig(CONFIG_USES, "shadow-provider")
                .withConfig(CONFIG_AFTER, "30")
                .withConfig("enabled", "true")
                .withConfig("roles", "admin", "user")
                .build();

        String json = JsonSerialization.writeValueAsString(step);
        JsonNode root = JsonSerialization.readValue(json, JsonNode.class);

        assertThat(root.get(CONFIG_USES).asText()).isEqualTo("notify-user");
        assertThat(root.get(CONFIG_AFTER).asText()).isEqualTo("30");

        assertThat(root.has(CONFIG_WITH)).isTrue();
        JsonNode config = root.get(CONFIG_WITH);
        assertThat(config.get("enabled").asBoolean()).isTrue();
        assertThat(config.get("roles").isArray()).isTrue();
        assertThat(config.get("roles").get(0).asText()).isEqualTo("admin");
        assertThat(config.get("roles").get(1).asText()).isEqualTo("user");
        assertThat(config.has(CONFIG_USES)).isFalse();
        assertThat(config.has(CONFIG_AFTER)).isFalse();
    }
}
