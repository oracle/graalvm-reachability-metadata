/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.structured.Description;
import org.junit.jupiter.api.Test;

class ServiceOutputParserTest {

    @Test
    void includesAnnotatedEnumDescriptionsInFormatInstructions() {
        ServiceOutputParser serviceOutputParser = new ServiceOutputParser();

        String formatInstructions = serviceOutputParser.outputFormatInstructions(AssistantResponse.class);

        assertThat(formatInstructions)
                .contains("\nAPPROVED - safe to return to the user")
                .contains("\nREJECTED - requires escalation")
                .contains("\nUNKNOWN");
    }

    private enum AssistantResponse {

        @Description("safe to return to the user")
        APPROVED,

        @Description({"requires", "escalation"})
        REJECTED,

        UNKNOWN
    }
}
