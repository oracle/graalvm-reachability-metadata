/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Triages support tickets through langchain4j AI services: declarative service interfaces whose
 * system and user message templates are filled from method arguments and whose answers are
 * parsed into enums, records and result wrappers.
 *
 * The help desk declares a `TicketTriage` interface and lets `AiServices` implement it on top of
 * an Ollama chat model. The test plays the Ollama server with an in-process HTTP endpoint that
 * records each chat request and answers like a model would, so the scenario can check both the
 * prompts the service rendered and the typed values the application receives back.
 */
class SupportTicketTriageServiceTest {

    private static final String TICKET = "Order 4711 arrived with a cracked screen and I need it for work on Monday.";

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private HttpServer ollama;

    private TicketTriage triage;

    enum Urgency {
        LOW, NORMAL, HIGH
    }

    record OrderIssue(String orderNumber, String product, boolean damaged) {
    }

    interface TicketTriage {

        @SystemMessage("You triage customer support tickets for {{shop}}.")
        @UserMessage("Classify the urgency of this ticket: {{ticket}}")
        Urgency classify(@V("shop") String shop, @V("ticket") String ticket);

        @SystemMessage("You extract order facts from support tickets.")
        @UserMessage("Extract the order issue from: {{it}}")
        OrderIssue extractIssue(String ticket);

        @SystemMessage("You write one sentence replies signed by {{agent}}.")
        Result<String> draftReply(@V("agent") String agent, @UserMessage String ticket);
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
        triage = AiServices.builder(TicketTriage.class)
                .chatModel(model)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void classifiesUrgencyFromTemplatedSystemAndUserMessages() {
        Urgency urgency = triage.classify("Northwind Outlet", TICKET);

        assertThat(urgency).isEqualTo(Urgency.HIGH);
        assertThat(chatRequests).singleElement().satisfies(request -> assertThat(request)
                .contains("You triage customer support tickets for Northwind Outlet.")
                .contains("Classify the urgency of this ticket: " + TICKET)
                .contains("LOW", "NORMAL", "HIGH"));
    }

    @Test
    void extractsStructuredOrderIssueIntoRecord() {
        OrderIssue issue = triage.extractIssue(TICKET);

        assertThat(issue).isEqualTo(new OrderIssue("4711", "tablet", true));
        assertThat(chatRequests).singleElement().satisfies(request -> assertThat(request)
                .contains("You extract order facts from support tickets.")
                .contains("Extract the order issue from: " + TICKET)
                .contains("orderNumber", "product", "damaged"));
    }

    @Test
    void draftsReplyAndReportsTokenUsage() {
        Result<String> reply = triage.draftReply("Dana", TICKET);

        assertThat(reply.content())
                .isEqualTo("We are sorry about order 4711 and a replacement ships today. - Dana");
        assertThat(reply.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(reply.tokenUsage().inputTokenCount()).isEqualTo(42);
        assertThat(reply.tokenUsage().outputTokenCount()).isEqualTo(17);
        assertThat(reply.tokenUsage().totalTokenCount()).isEqualTo(59);
        assertThat(chatRequests).singleElement().satisfies(request -> assertThat(request)
                .contains("You write one sentence replies signed by Dana.")
                .contains(TICKET));
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        String answer;
        if (request.contains("You triage customer support tickets")) {
            answer = "HIGH";
        } else if (request.contains("You extract order facts")) {
            answer = """
                    {"orderNumber": "4711", "product": "tablet", "damaged": true}""";
        } else {
            answer = "We are sorry about order 4711 and a replacement ships today. - Dana";
        }
        String response = """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z",
                 "message": {"role": "assistant", "content": "%s"},
                 "done": true, "done_reason": "stop", "prompt_eval_count": 42, "eval_count": 17}
                """.formatted(answer.replace("\\", "\\\\").replace("\"", "\\\""));

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
