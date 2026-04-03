/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.InputGuardrail;
import org.junit.jupiter.api.Test;

class DefaultGuardrailServiceTest {

    @Test
    void resolvesAnnotatedGuardrailsByMethodName() {
        DefaultGuardrailService guardrailService =
                (DefaultGuardrailService) GuardrailService.builder(GuardedAssistant.class).build();

        assertThat(guardrailService.getInputGuardrails("answer"))
                .singleElement()
                .isInstanceOf(RecordingInputGuardrail.class);
    }

    interface GuardedAssistant {

        @InputGuardrails(RecordingInputGuardrail.class)
        String answer(String prompt);
    }

    public static final class RecordingInputGuardrail implements InputGuardrail {}
}
