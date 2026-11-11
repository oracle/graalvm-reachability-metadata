/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_agentscope.agentscope_extensions_studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class PushMessageRequestTest {
    @Test
    @Timeout(60)
    void sendsAgentMessageForVisualization() throws Exception {
        Msg message = Msg.builder()
                .id("message-42")
                .name("Research assistant")
                .role(MsgRole.ASSISTANT)
                .textContent("Found a useful result")
                .build();
        try (StudioServer studio = new StudioServer()) {
            studio.client().pushMessage(message).block(Duration.ofSeconds(30));

            JsonNode payload = studio.takePayload("/trpc/pushMessage");
            assertEquals("studio-test-run", payload.path("runId").asText());
            assertFalse(payload.path("replyId").asText().isBlank());
            assertEquals("Research assistant", payload.path("name").asText());
            assertEquals("assistant", payload.path("role").asText());
            JsonNode sentMessage = payload.path("msg");
            assertEquals("message-42", sentMessage.path("id").asText());
            assertEquals("Research assistant", sentMessage.path("name").asText());
            assertEquals("Found a useful result", sentMessage.path("content").path(0).path("text").asText());
        }
    }
}
