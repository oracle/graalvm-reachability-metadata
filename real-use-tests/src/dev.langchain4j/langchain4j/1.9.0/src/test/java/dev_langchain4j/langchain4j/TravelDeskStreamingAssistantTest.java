/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Streams travel desk answers through langchain4j streaming AI services: service methods that
 * return a `TokenStream` over an Ollama streaming chat model, delivering partial responses as
 * they arrive, running tools between streamed turns, and reporting failures to the error callback.
 *
 * The airline's web front end shows each answer while the model is still writing it, so it wires
 * the stream callbacks to its own sinks and waits for the completed response. The test plays the
 * Ollama server with an in-process HTTP endpoint that answers every chat request as a stream of
 * newline-delimited JSON chunks, the wire format Ollama uses when `stream` is enabled.
 */
class TravelDeskStreamingAssistantTest {

    private static final long TIMEOUT_SECONDS = 30;

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private final FlightBoard flightBoard = new FlightBoard();

    private HttpServer ollama;

    private TravelDesk travelDesk;

    interface TravelDesk {

        @SystemMessage("You are the live travel desk of {{airline}}. Answer in one sentence.")
        TokenStream ask(@V("airline") String airline, @UserMessage String question);
    }

    static class FlightBoard {

        private final List<String> lookups = new CopyOnWriteArrayList<>();

        @Tool("Returns the departure status of a flight")
        String flightStatus(String flightNumber) {
            lookups.add(flightNumber);
            return flightNumber + " departs from gate B12, delayed by 25 minutes";
        }
    }

    @BeforeEach
    void startModelServer() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/chat", this::streamChat);
        ollama.start();

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl("http://localhost:" + ollama.getAddress().getPort())
                .modelName("llama3.1")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        travelDesk = AiServices.builder(TravelDesk.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(flightBoard)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void streamsPartialResponsesBeforeTheCompletedAnswer() throws Exception {
        List<String> partials = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();

        travelDesk.ask("Skyline Air", "How much cabin baggage may I bring?")
                .onPartialResponse(partials::add)
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();
        ChatResponse response = completed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(partials).containsExactly("You may bring ", "one bag of ", "up to 8 kg on board.");
        assertThat(response.aiMessage().text()).isEqualTo(String.join("", partials));
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(38);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(12);
        assertThat(chatRequests).singleElement().satisfies(request -> assertThat(request)
                .containsPattern("\"stream\"\\s*:\\s*true")
                .contains("You are the live travel desk of Skyline Air. Answer in one sentence.")
                .contains("How much cabin baggage may I bring?"));
    }

    @Test
    void runsToolRequestedMidStreamAndStreamsTheFollowUpAnswer() throws Exception {
        List<String> partials = new CopyOnWriteArrayList<>();
        List<BeforeToolExecution> announced = new CopyOnWriteArrayList<>();
        List<ToolExecution> executed = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();

        travelDesk.ask("Skyline Air", "Is flight SK204 on time?")
                .onPartialResponse(partials::add)
                .beforeToolExecution(announced::add)
                .onToolExecuted(executed::add)
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();
        ChatResponse response = completed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(flightBoard.lookups).containsExactly("SK204");
        assertThat(announced).singleElement()
                .satisfies(before -> assertThat(before.request().name()).isEqualTo("flightStatus"));
        assertThat(executed).singleElement().satisfies(execution -> {
            assertThat(execution.request().name()).isEqualTo("flightStatus");
            assertThat(execution.request().arguments()).contains("SK204");
            assertThat(execution.result()).isEqualTo("SK204 departs from gate B12, delayed by 25 minutes");
        });
        assertThat(partials).containsExactly("SK204 is delayed ", "by 25 minutes ", "and boards at gate B12.");
        assertThat(response.aiMessage().text()).isEqualTo("SK204 is delayed by 25 minutes and boards at gate B12.");
        assertThat(chatRequests).hasSize(2);
        assertThat(chatRequests.get(0)).contains("Returns the departure status of a flight");
        assertThat(chatRequests.get(1)).contains("SK204 departs from gate B12, delayed by 25 minutes");
    }

    @Test
    void reportsServerFailureToTheErrorCallback() throws Exception {
        List<String> partials = new CopyOnWriteArrayList<>();
        CompletableFuture<Throwable> failed = new CompletableFuture<>();

        travelDesk.ask("Skyline Air", "Please rebook me during the outage.")
                .onPartialResponse(partials::add)
                .onCompleteResponse(response -> failed.completeExceptionally(
                        new AssertionError("Unexpected completion: " + response)))
                .onError(failed::complete)
                .start();
        Throwable error = failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(error).hasMessageContaining("model runner is unavailable");
        assertThat(partials).isEmpty();
    }

    private void streamChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        if (request.contains("during the outage")) {
            byte[] error = """
                    {"error": "model runner is unavailable"}""".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, error.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(error);
            }
            return;
        }

        List<String> chunks;
        if (request.contains("cabin baggage")) {
            chunks = List.of(
                    content("You may bring "),
                    content("one bag of "),
                    content("up to 8 kg on board."),
                    done(38, 12));
        } else if (!request.contains("delayed by 25 minutes")) {
            String parameter = flightStatusParameter(request);
            chunks = List.of(
                    """
                    {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z", "message": {"role": "assistant",\
                     "content": "", "tool_calls": [{"function": {"name": "flightStatus",\
                     "arguments": {"%s": "SK204"}}}]}, "done": false}""".formatted(parameter),
                    done(51, 9));
        } else {
            chunks = List.of(
                    content("SK204 is delayed "),
                    content("by 25 minutes "),
                    content("and boards at gate B12."),
                    done(73, 14));
        }

        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream body = exchange.getResponseBody()) {
            for (String chunk : chunks) {
                body.write((chunk + "\n").getBytes(StandardCharsets.UTF_8));
                body.flush();
            }
        }
    }

    private static String content(String text) {
        return """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z",\
                 "message": {"role": "assistant", "content": "%s"}, "done": false}""".formatted(text);
    }

    private static String done(int promptTokens, int outputTokens) {
        return """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:31Z",\
                 "message": {"role": "assistant", "content": ""}, "done": true, "done_reason": "stop",\
                 "prompt_eval_count": %d, "eval_count": %d}""".formatted(promptTokens, outputTokens);
    }

    private static String flightStatusParameter(String request) {
        Matcher tool = Pattern.compile(
                "\"name\"\\s*:\\s*\"flightStatus\".*?\"required\"\\s*:\\s*\\[\\s*\"([^\"]+)\"", Pattern.DOTALL)
                .matcher(request);
        assertThat(tool.find()).as("schema of tool flightStatus").isTrue();
        return tool.group(1);
    }
}
