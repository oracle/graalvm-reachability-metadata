/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_model;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.augment.AugmentedArgumentEvent;
import org.springframework.ai.tool.augment.AugmentedToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class AugmentedToolCallbackTest {

    @Test
    void augmentsToolSchemaAndProcessesRecordArguments() {
        CapturingToolCallback delegate = new CapturingToolCallback();
        AtomicReference<AugmentedArgumentEvent<RequestMetadata>> event = new AtomicReference<>();

        AugmentedToolCallback<RequestMetadata> callback = new AugmentedToolCallback<>(delegate,
                RequestMetadata.class, event::set, true);

        assertThat(callback.getToolDefinition().name()).isEqualTo("search");
        assertThat(callback.getToolDefinition().inputSchema())
                .contains("\"tenantId\"", "Tenant identifier", "\"priority\"", "Request priority");

        String result = callback.call("{\"tenantId\":\"acme\",\"priority\":3}");

        assertThat(result).isEqualTo("delegated");
        assertThat(delegate.toolInput()).doesNotContain("tenantId", "priority");
        assertThat(event.get().arguments()).isEqualTo(new RequestMetadata("acme", 3));
        assertThat(event.get().rawInput()).contains("tenantId", "priority");
    }

    public record RequestMetadata(
            @ToolParam(description = "Tenant identifier") String tenantId,
            @ToolParam(description = "Request priority", required = false) int priority) {
    }

    private static final class CapturingToolCallback implements ToolCallback {

        private String toolInput;

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name("search")
                    .description("Perform a search")
                    .inputSchema("""
                            {
                              "type": "object",
                              "properties": {
                                "query": {
                                  "type": "string",
                                  "description": "Search query"
                                }
                              }
                            }
                            """)
                    .build();
        }

        @Override
        public String call(String toolInput) {
            this.toolInput = toolInput;
            return "delegated";
        }

        String toolInput() {
            return this.toolInput;
        }

    }

}
