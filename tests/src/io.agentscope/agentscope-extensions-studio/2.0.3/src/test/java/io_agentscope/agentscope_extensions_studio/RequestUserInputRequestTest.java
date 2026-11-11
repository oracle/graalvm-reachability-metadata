/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_agentscope.agentscope_extensions_studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class RequestUserInputRequestTest {
    @Test
    @Timeout(60)
    void requestsStructuredInputAndReturnsItsCorrelationId() throws Exception {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("answer", Map.of("type", "string")),
                "required", List.of("answer"));
        try (StudioServer studio = new StudioServer()) {
            String requestId = studio.client()
                    .requestUserInput("agent-7", "Planning assistant", schema)
                    .block(Duration.ofSeconds(30));

            assertNotNull(requestId);
            assertTrue(!requestId.isBlank());
            JsonNode payload = studio.takePayload("/trpc/requestUserInput");
            assertEquals(requestId, payload.path("requestId").asText());
            assertEquals("studio-test-run", payload.path("runId").asText());
            assertEquals("agent-7", payload.path("agentId").asText());
            assertEquals("Planning assistant", payload.path("agentName").asText());
            JsonNode input = payload.path("structuredInput");
            assertEquals("object", input.path("type").asText());
            assertEquals("string", input.path("properties").path("answer").path("type").asText());
            assertEquals("answer", input.path("required").path(0).asText());
        }
    }

    @Test
    @Timeout(60)
    void requestsPlainInputWithoutAStructuredSchema() throws Exception {
        try (StudioServer studio = new StudioServer()) {
            String requestId = studio.client()
                    .requestUserInput("agent-8", "Chat assistant", null)
                    .block(Duration.ofSeconds(30));

            JsonNode payload = studio.takePayload("/trpc/requestUserInput");
            assertNotNull(requestId);
            assertEquals(requestId, payload.path("requestId").asText());
            assertEquals("agent-8", payload.path("agentId").asText());
            assertTrue(payload.path("structuredInput").isObject());
            assertTrue(payload.path("structuredInput").isEmpty());
        }
    }
}
