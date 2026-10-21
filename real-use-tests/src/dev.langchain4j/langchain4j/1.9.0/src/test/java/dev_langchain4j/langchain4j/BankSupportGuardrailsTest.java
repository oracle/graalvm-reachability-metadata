/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Protects a bank's support assistant with langchain4j guardrails: input guardrails that block
 * or rewrite the customer's message before it reaches the model, and an output guardrail that
 * reprompts the model when its answer leaks internal reference codes.
 *
 * The guardrails are declared on the AI service method with `@InputGuardrails` and
 * `@OutputGuardrails`, so langchain4j instantiates them from their classes, and the service keeps
 * a chat memory so a reprompt reaches the model with the conversation. The test plays the
 * Ollama server, records every chat request, and leaks an internal code until the request carries
 * the reprompt instruction, or always for the card question, so every outcome is deterministic.
 */
class BankSupportGuardrailsTest {

    private static final String REPROMPT = "Answer again without any internal reference code.";

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private HttpServer ollama;

    private BankAssistant assistant;

    interface BankAssistant {

        @SystemMessage("You are the support assistant of Northwind Bank.")
        @InputGuardrails({BlockCredentialQuestions.class, RedactAccountNumbers.class})
        @OutputGuardrails(value = NoInternalReferenceCodes.class, maxRetries = 2)
        String chat(String question);
    }

    /** Refuses questions about credentials before the model ever sees them. */
    public static final class BlockCredentialQuestions implements InputGuardrail {

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            if (userMessage.singleText().toLowerCase().contains("password")) {
                return fatal("Support never discusses passwords or PINs.");
            }
            return success();
        }
    }

    /** Replaces account numbers with a placeholder so they never leave the bank. */
    public static final class RedactAccountNumbers implements InputGuardrail {

        private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\b\\d{8,}\\b");

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            String text = userMessage.singleText();
            String redacted = ACCOUNT_NUMBER.matcher(text).replaceAll("[ACCOUNT]");
            return redacted.equals(text) ? success() : successWith(redacted);
        }
    }

    /** Asks the model to rephrase whenever its answer contains an internal reference code. */
    public static final class NoInternalReferenceCodes implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (responseFromLLM.text().contains("INTERNAL-")) {
                return reprompt("The answer leaked an internal reference code.", REPROMPT);
            }
            return success();
        }
    }

    @BeforeEach
    void startModelServer() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/chat", this::answerChat);
        ollama.start();

        ChatModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:" + ollama.getAddress().getPort())
                .modelName("llama3.1")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();
        assistant = AiServices.builder(BankAssistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void redactsAccountNumbersBeforeTheyReachTheModel() {
        String answer = assistant.chat("What is the balance of account 12345678901?");

        assertThat(answer).isEqualTo("I can help with that once you sign in to online banking.");
        assertThat(chatRequests).hasSize(1);
        assertThat(chatRequests.get(0))
                .contains("You are the support assistant of Northwind Bank.")
                .contains("What is the balance of account [ACCOUNT]?")
                .doesNotContain("12345678901");
    }

    @Test
    void blocksCredentialQuestionsWithoutCallingTheModel() {
        assertThatThrownBy(() -> assistant.chat("Can you reset my online banking password?"))
                .isInstanceOf(InputGuardrailException.class)
                .hasMessageContaining("Support never discusses passwords or PINs.");

        assertThat(chatRequests).isEmpty();
    }

    @Test
    void repromptsTheModelUntilTheAnswerIsClean() {
        String answer = assistant.chat("Where is my wire transfer?");

        assertThat(answer).isEqualTo("Your wire transfer is on its way and arrives tomorrow.");
        assertThat(chatRequests).hasSize(2);
        assertThat(chatRequests.get(0)).contains("Where is my wire transfer?").doesNotContain(REPROMPT);
        assertThat(chatRequests.get(1)).contains("Where is my wire transfer?", REPROMPT);
    }

    @Test
    void failsOnceTheRepromptBudgetIsSpent() {
        assertThatThrownBy(() -> assistant.chat("Why was my card declined?"))
                .isInstanceOf(OutputGuardrailException.class)
                .hasMessageContaining("The answer leaked an internal reference code.");

        assertThat(chatRequests).hasSize(2);
        assertThat(chatRequests).allSatisfy(request -> assertThat(request).contains("Why was my card declined?"));
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        String content;
        if (request.contains("card declined")) {
            content = "Your card was declined under rule INTERNAL-4410.";
        } else if (request.contains("wire transfer")) {
            content = request.contains(REPROMPT)
                    ? "Your wire transfer is on its way and arrives tomorrow."
                    : "Transfer INTERNAL-7731 is queued and arrives tomorrow.";
        } else {
            content = "I can help with that once you sign in to online banking.";
        }
        String response = """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z",
                 "message": {"role": "assistant", "content": "%s"},
                 "done": true, "done_reason": "stop", "prompt_eval_count": 32, "eval_count": 12}
                """.formatted(content);

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
