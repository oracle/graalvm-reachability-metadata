/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j.guardrail;

import dev.langchain4j.service.guardrail.GuardrailService;
import dev.langchain4j.service.guardrail.InputGuardrails;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.InputGuardrail;
import org.junit.jupiter.api.Test;

class DefaultGuardrailServiceTest {

    @Test
    void resolvesAnnotatedGuardrailsByMethod() throws NoSuchMethodException {
        GuardrailService guardrailService =
                GuardrailService.builder(GuardedAssistant.class).build();

        assertThat(guardrailService.hasInputGuardrails(
                GuardedAssistant.class.getMethod("answer", String.class))).isTrue();
    }

    public interface GuardedAssistant {

        @InputGuardrails(RecordingInputGuardrail.class)
        String answer(String prompt);
    }

    public static final class RecordingInputGuardrail implements InputGuardrail {}
}
