/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaLanguageModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModelCard;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.ollama.RunningOllamaModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Langchain4j_ollamaTest {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String CHAT_MODEL = "ollama-chat-test";
    private static final String EMBEDDING_MODEL = "ollama-embedding-test";

    @Test
    @Timeout(55)
    void chatsWithImagesAndMapsToolCallsFromLocalOllamaResponse() throws Exception {
        try (OllamaServer server = OllamaServer.start(ResponseKind.CHAT)) {
            OllamaChatModel model = OllamaChatModel.builder()
                    .baseUrl(server.baseUrl())
                    .modelName(CHAT_MODEL)
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .returnThinking(true)
                    .temperature(0.2)
                    .customHeaders(Map.of("X-Ollama-Test", "chat-header"))
                    .build();

            ChatResponse response = model.chat(UserMessage.from(
                    TextContent.from("Inspect this small image"), ImageContent.from("aGVsbG8=", "image/png")));

            assertThat(response.aiMessage().text()).isEqualTo("I inspected the image.");
            assertThat(response.aiMessage().thinking()).isEqualTo("Analyzing pixels");
            assertThat(response.aiMessage().toolExecutionRequests()).singleElement().satisfies(toolCall -> {
                assertThat(toolCall.id()).isEqualTo("call-17");
                assertThat(toolCall.name()).isEqualTo("record_result");
                assertThat(toolCall.arguments()).contains("city", "Prague");
            });
            assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
            assertThat(response.modelName()).isEqualTo(CHAT_MODEL);
            assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(8);
            assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(5);

            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/api/chat");
            assertThat(request.header()).isEqualTo("chat-header");
            assertThat(request.body())
                    .contains(
                            CHAT_MODEL,
                            "Inspect this small image",
                            "aGVsbG8=",
                            "temperature",
                            "0.2",
                            "stream",
                            "false");
        }
    }

    @Test
    @Timeout(55)
    void streamsChatAndBuildsCompleteResponseFromNdjson() throws Exception {
        try (OllamaServer server = OllamaServer.start(ResponseKind.STREAMING_CHAT)) {
            OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                    .baseUrl(server.baseUrl())
                    .modelName(CHAT_MODEL)
                    .timeout(HTTP_TIMEOUT)
                    .returnThinking(true)
                    .build();
            CountDownLatch completed = new CountDownLatch(1);
            List<String> partialResponses = new CopyOnWriteArrayList<>();
            AtomicReference<ChatResponse> completeResponse = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            model.chat("Stream an Ollama greeting", new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                    partialResponses.add(partialResponse.text());
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    completeResponse.set(response);
                    completed.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    failure.set(error);
                    completed.countDown();
                }
            });

            assertThat(completed.await(HTTP_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(partialResponses).containsExactly("Hello ", "from Ollama!");
            assertThat(completeResponse.get().aiMessage().text()).isEqualTo("Hello from Ollama!");
            assertThat(completeResponse.get().aiMessage().thinking()).isEqualTo("Plan answer");
            assertThat(completeResponse.get().finishReason()).isEqualTo(FinishReason.STOP);
            assertThat(completeResponse.get().tokenUsage().totalTokenCount()).isEqualTo(10);

            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/api/chat");
            assertThat(request.body()).contains(CHAT_MODEL, "Stream an Ollama greeting", "stream", "true");
        }
    }

    @Test
    @Timeout(55)
    void embedsMultipleSegmentsFromLocalOllamaResponse() throws Exception {
        try (OllamaServer server = OllamaServer.start(ResponseKind.EMBEDDING)) {
            OllamaEmbeddingModel model = OllamaEmbeddingModel.builder()
                    .baseUrl(server.baseUrl())
                    .modelName(EMBEDDING_MODEL)
                    .dimensions(3)
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .customHeaders(Map.of("X-Ollama-Test", "embedding-header"))
                    .build();

            Response<List<Embedding>> response = model.embedAll(
                    List.of(TextSegment.from("first embedding input"), TextSegment.from("second embedding input")));

            assertThat(response.content()).hasSize(2);
            assertThat(response.content().get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f);
            assertThat(response.content().get(1).vector()).containsExactly(-0.4f, 0.5f, 0.6f);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(9);

            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/api/embed");
            assertThat(request.header()).isEqualTo("embedding-header");
            assertThat(request.body())
                    .contains(
                            EMBEDDING_MODEL,
                            "first embedding input",
                            "second embedding input",
                            "dimensions",
                            "3");
        }
    }

    @Test
    @Timeout(55)
    void generatesTextFromLocalOllamaCompletionResponse() throws Exception {
        try (OllamaServer server = OllamaServer.start(ResponseKind.COMPLETION)) {
            OllamaLanguageModel model = OllamaLanguageModel.builder()
                    .baseUrl(server.baseUrl())
                    .modelName("ollama-completion-test")
                    .temperature(0.4)
                    .topK(12)
                    .numPredict(20)
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            Response<String> response = model.generate("Complete this sentence");

            assertThat(response.content()).isEqualTo("with a local Ollama response.");
            assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(4);
            assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(6);
            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/api/generate");
            assertThat(request.body())
                    .contains(
                            "ollama-completion-test",
                            "Complete this sentence",
                            "temperature",
                            "0.4",
                            "top_k",
                            "12",
                            "num_predict",
                            "20",
                            "stream",
                            "false");
        }
    }

    @Test
    @Timeout(55)
    void managesAvailableAndRunningModelsThroughLocalOllamaApi() throws Exception {
        try (OllamaServer server = OllamaServer.start(ResponseKind.MODEL_MANAGEMENT)) {
            OllamaModels models = OllamaModels.builder()
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            OllamaModel availableModel = models.availableModels().content().get(0);
            assertThat(availableModel.getName()).isEqualTo("granite-code:latest");
            assertThat(availableModel.getSize()).isEqualTo(4200L);
            assertThat(availableModel.getDetails().getFamily()).isEqualTo("granite");

            OllamaModelCard modelCard = models.modelCard(availableModel).content();
            assertThat(modelCard.getLicense()).isEqualTo("Apache-2.0");
            assertThat(modelCard.getCapabilities()).containsExactly("completion", "tools");
            assertThat(modelCard.getDetails().getParameterSize()).isEqualTo("8B");

            RunningOllamaModel runningModel = models.runningModels().content().get(0);
            assertThat(runningModel.getName()).isEqualTo("granite-code:latest");
            assertThat(runningModel.getSizeVram()).isEqualTo(2048L);
            assertThat(runningModel.getContextLength()).isEqualTo(8192);

            models.deleteModel(availableModel);

            assertThat(server.requests()).extracting(RequestRecord::path).containsExactly(
                    "/api/tags", "/api/show", "/api/ps", "/api/delete");
            assertThat(server.requests().get(1).body()).contains("granite-code:latest");
            assertThat(server.requests().get(3).method()).isEqualTo("DELETE");
            assertThat(server.requests().get(3).body()).contains("granite-code:latest");
        }
    }

    private enum ResponseKind {
        CHAT,
        STREAMING_CHAT,
        EMBEDDING,
        COMPLETION,
        MODEL_MANAGEMENT
    }

    private record RequestRecord(String method, String path, String header, String body) {}

    private static final class OllamaServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final ResponseKind responseKind;
        private final CopyOnWriteArrayList<RequestRecord> requests = new CopyOnWriteArrayList<>();

        private OllamaServer(HttpServer server, ExecutorService executor, ResponseKind responseKind) {
            this.server = server;
            this.executor = executor;
            this.responseKind = responseKind;
        }

        static OllamaServer start(ResponseKind responseKind) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            OllamaServer ollamaServer = new OllamaServer(server, executor, responseKind);
            server.createContext("/", ollamaServer::handle);
            server.setExecutor(executor);
            server.start();
            return ollamaServer;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        RequestRecord singleRequest() {
            assertThat(requests).hasSize(1);
            return requests.get(0);
        }

        List<RequestRecord> requests() {
            return List.copyOf(requests);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String requestPath = exchange.getRequestURI().getPath();
                requests.add(new RequestRecord(
                        exchange.getRequestMethod(),
                        requestPath,
                        exchange.getRequestHeaders().getFirst("X-Ollama-Test"),
                        requestBody));

                String responseBody = switch (responseKind) {
                    case CHAT -> chatResponse();
                    case STREAMING_CHAT -> streamingChatResponse();
                    case EMBEDDING -> embeddingResponse();
                    case COMPLETION -> completionResponse();
                    case MODEL_MANAGEMENT -> modelManagementResponse(requestPath);
                };
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders()
                        .set(
                                "Content-Type",
                                responseKind == ResponseKind.STREAMING_CHAT
                                        ? "application/x-ndjson"
                                        : "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        }

        private static String chatResponse() {
            return """
                    {
                      "model": "ollama-chat-test",
                      "created_at": "2026-01-02T03:04:05Z",
                      "message": {
                        "role": "assistant",
                        "content": "I inspected the image.",
                        "thinking": "Analyzing pixels",
                        "tool_calls": [{
                          "id": "call-17",
                          "function": {
                            "name": "record_result",
                            "arguments": {"city": "Prague", "accepted": true}
                          }
                        }]
                      },
                      "done_reason": "stop",
                      "done": true,
                      "prompt_eval_count": 8,
                      "eval_count": 5
                    }
                    """.trim();
        }

        private static String streamingChatResponse() {
            return "{\"model\":\"ollama-chat-test\",\"message\":{\"role\":\"assistant\","
                    + "\"thinking\":\"Plan \",\"content\":\"Hello \"},\"done\":false}\n"
                    + "{\"model\":\"ollama-chat-test\",\"message\":{\"role\":\"assistant\","
                    + "\"thinking\":\"answer\",\"content\":\"from Ollama!\"},\"done\":false}\n"
                    + "{\"model\":\"ollama-chat-test\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},"
                    + "\"done_reason\":\"stop\",\"done\":true,\"prompt_eval_count\":4,\"eval_count\":6}\n";
        }

        private static String embeddingResponse() {
            return """
                    {
                      "model": "ollama-embedding-test",
                      "embeddings": [[0.1, 0.2, 0.3], [-0.4, 0.5, 0.6]],
                      "prompt_eval_count": 9
                    }
                    """.trim();
        }

        private static String completionResponse() {
            return """
                    {
                      "model": "ollama-completion-test",
                      "created_at": "2026-01-02T03:04:05Z",
                      "response": "with a local Ollama response.",
                      "done": true,
                      "done_reason": "stop",
                      "prompt_eval_count": 4,
                      "eval_count": 6
                    }
                    """.trim();
        }

        private static String modelManagementResponse(String path) {
            return switch (path) {
                case "/api/tags" -> """
                        {"models":[{
                          "name":"granite-code:latest",
                          "model":"granite-code:latest",
                          "modified_at":"2026-01-02T03:04:05Z",
                          "size":4200,
                          "digest":"sha256:available",
                          "details":{"format":"gguf","family":"granite","parameter_size":"8B"}
                        }]}
                        """.trim();
                case "/api/show" -> """
                        {
                          "license":"Apache-2.0",
                          "modelfile":"FROM granite-code",
                          "details":{"family":"granite","parameter_size":"8B"},
                          "capabilities":["completion","tools"]
                        }
                        """.trim();
                case "/api/ps" -> """
                        {"models":[{
                          "name":"granite-code:latest",
                          "model":"granite-code:latest",
                          "size":4200,
                          "digest":"sha256:running",
                          "details":{"family":"granite"},
                          "expires_at":"2026-01-02T03:14:05Z",
                          "size_vram":2048,
                          "context_length":8192
                        }]}
                        """.trim();
                case "/api/delete" -> "{}";
                default -> throw new IllegalArgumentException("Unexpected Ollama API path: " + path);
            };
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(HTTP_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }
}
