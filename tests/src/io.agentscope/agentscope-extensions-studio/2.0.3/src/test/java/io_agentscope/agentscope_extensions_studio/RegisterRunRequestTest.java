/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_agentscope.agentscope_extensions_studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class RegisterRunRequestTest {
    @Test
    @Timeout(60)
    void registersRunWithConfiguredIdentityAndProcessDetails() throws Exception {
        try (StudioServer studio = new StudioServer()) {
            studio.client().registerRun().block(Duration.ofSeconds(30));

            JsonNode payload = studio.takePayload("/trpc/registerRun");
            assertEquals("studio-test-run", payload.path("id").asText());
            assertEquals("Support \"project\"", payload.path("project").asText());
            assertEquals("Public API run", payload.path("name").asText());
            assertTrue(payload.path("timestamp").asText()
                    .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
            assertEquals(ProcessHandle.current().pid(), payload.path("pid").asLong());
            assertEquals("running", payload.path("status").asText());
            assertTrue(payload.has("run_dir"));
            assertEquals("", payload.path("run_dir").asText());
        }
    }
}
