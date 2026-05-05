/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.workflows.WorkflowStepRepresentation;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MultivaluedHashMapValueSerializerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesOnlyConfigurationEntriesThatAreNotExposedAsStepProperties() throws Exception {
        WorkflowStepRepresentation step = WorkflowStepRepresentation.create()
                .of("reviewer-provider")
                .after("30")
                .withConfig("uses", "ignored-provider")
                .withConfig("priority", "10")
                .withConfig("enabled", "true")
                .withConfig("reviewers", "alice", "bob")
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(step));

        assertThat(json.get("uses").asText()).isEqualTo("reviewer-provider");
        assertThat(json.get("after").asText()).isEqualTo("30");
        assertThat(json.has("priority")).isFalse();

        JsonNode config = json.get("with");
        assertThat(config.has("uses")).isFalse();
        assertThat(config.has("after")).isFalse();
        assertThat(config.has("priority")).isFalse();
        assertThat(config.get("enabled").asBoolean()).isTrue();

        List<String> reviewers = new ArrayList<>();
        config.get("reviewers").forEach(value -> reviewers.add(value.asText()));
        assertThat(reviewers).containsExactly("alice", "bob");
    }

    @Test
    void treatsConfigurationAsEmptyWhenAllEntriesAreRepresentedByStepProperties() throws Exception {
        WorkflowStepRepresentation step = WorkflowStepRepresentation.create()
                .of("reviewer-provider")
                .after("30")
                .withConfig("uses", "ignored-provider")
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(step));

        assertThat(json.get("uses").asText()).isEqualTo("reviewer-provider");
        assertThat(json.get("after").asText()).isEqualTo("30");
        assertThat(json.has("with")).isFalse();
    }
}
