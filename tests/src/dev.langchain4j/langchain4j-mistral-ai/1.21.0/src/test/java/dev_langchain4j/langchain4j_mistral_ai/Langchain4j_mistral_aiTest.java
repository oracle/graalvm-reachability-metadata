/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_mistral_ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatResponseMetadata;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Langchain4j_mistral_aiTest {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String CHAT_MODEL = "mistral-test-chat";
    private static final String EMBEDDING_MODEL = "mistral-test-embed";
    private static final String MODERATION_MODEL = "mistral-test-moderation";

    @Test
    @Timeout(55)
    void chatsAgainstLocalMistralEndpoint() throws Exception {
        try (MistralServer server = MistralServer.start()) {
            MistralAiChatModel model = MistralAiChatModel.builder()
                    .apiKey("chat-api-key")
                    .modelName(CHAT_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .safePrompt(true)
                    .returnThinking(true)
                    .build();

            ChatResponse response = model.chat(UserMessage.from("What is the capital of France?"));

            assertThat(response.aiMessage().text()).isEqualTo("Paris is the capital of France.");
            assertThat(response.aiMessage().thinking()).isEqualTo("I should answer directly.");
            assertThat(response.aiMessage().toolExecutionRequests()).singleElement().satisfies(toolCall -> {
                assertThat(toolCall.id()).isEqualTo("call-weather-1");
                assertThat(toolCall.name()).isEqualTo("weather");
                assertThat(toolCall.arguments()).isEqualTo("{\"city\":\"Paris\"}");
            });
            assertThat(response.id()).isEqualTo("chat-response-1");
            assertThat(response.modelName()).isEqualTo("mistral-response-model");
            assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
            assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(8);
            assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(6);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(14);

            MistralAiChatResponseMetadata metadata = (MistralAiChatResponseMetadata) response.metadata();
            assertThat(metadata.rawHttpResponse().statusCode()).isEqualTo(200);

            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.authorization()).isEqualTo("Bearer chat-api-key");
            assertThat(request.body()).contains(CHAT_MODEL, "What is the capital of France?", "safe_prompt");
        }
    }

    @Test
    @Timeout(55)
    void streamsChatAgainstLocalMistralEndpoint() throws Exception {
        try (MistralServer server = MistralServer.start()) {
            MistralAiStreamingChatModel model = MistralAiStreamingChatModel.builder()
                    .apiKey("stream-api-key")
                    .modelName(CHAT_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .build();
            CountDownLatch completed = new CountDownLatch(1);
            List<String> partialResponses = new CopyOnWriteArrayList<>();
            AtomicReference<ChatResponse> completeResponse = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            model.chat("Stream a greeting", new StreamingChatResponseHandler() {
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
            assertThat(partialResponses).containsExactly("Hello ", "from Mistral!");
            ChatResponse response = completeResponse.get();
            assertThat(response.aiMessage().text()).isEqualTo("Hello from Mistral!");
            assertThat(response.id()).isEqualTo("stream-response-1");
            assertThat(response.modelName()).isEqualTo("mistral-stream-model");
            assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(9);

            MistralAiChatResponseMetadata metadata = (MistralAiChatResponseMetadata) response.metadata();
            assertThat(metadata.rawHttpResponse().statusCode()).isEqualTo(200);
            assertThat(metadata.rawServerSentEvents()).hasSize(3);

            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.authorization()).isEqualTo("Bearer stream-api-key");
            assertThat(request.body()).contains(CHAT_MODEL, "Stream a greeting", "\"stream\"", "true");
        }
    }

    @Test
    @Timeout(55)
    void createsEmbeddingsAgainstLocalMistralEndpoint() throws Exception {
        try (MistralServer server = MistralServer.start()) {
            MistralAiEmbeddingModel model = MistralAiEmbeddingModel.builder()
                    .apiKey("embedding-api-key")
                    .modelName(EMBEDDING_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            Response<List<Embedding>> response = model.embedAll(
                    List.of(TextSegment.from("First sentence"), TextSegment.from("Second sentence")));

            assertThat(response.content()).hasSize(2);
            assertThat(response.content().get(0).vector()).containsExactly(0.125f, 0.5f, -0.25f);
            assertThat(response.content().get(1).vector()).containsExactly(-0.75f, 0.25f, 1.0f);
            assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(5);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(5);
            assertThat(model.modelName()).isEqualTo(EMBEDDING_MODEL);

            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/embeddings");
            assertThat(request.authorization()).isEqualTo("Bearer embedding-api-key");
            assertThat(request.body())
                    .contains(EMBEDDING_MODEL, "First sentence", "Second sentence", "encoding_format", "float");
        }
    }

    @Test
    @Timeout(55)
    void moderatesTextAgainstLocalMistralEndpoint() throws Exception {
        try (MistralServer server = MistralServer.start()) {
            MistralAiModerationModel model = MistralAiModerationModel.builder()
                    .apiKey("moderation-api-key")
                    .modelName(MODERATION_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            Response<Moderation> response = model.moderate("A threatening message");

            assertThat(response.content().flagged()).isTrue();
            assertThat(response.content().flaggedText()).isEqualTo("A threatening message");
            assertThat(model.modelName()).isEqualTo(MODERATION_MODEL);

            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/moderations");
            assertThat(request.authorization()).isEqualTo("Bearer moderation-api-key");
            assertThat(request.body()).contains(MODERATION_MODEL, "A threatening message");
        }
    }

    private record RequestRecord(String path, String authorization, String body) {}

    private static final class MistralServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final CopyOnWriteArrayList<RequestRecord> requests = new CopyOnWriteArrayList<>();

        private MistralServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static MistralServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            MistralServer mistralServer = new MistralServer(server, executor);
            server.createContext("/", mistralServer::handle);
            server.setExecutor(executor);
            server.start();
            return mistralServer;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        RequestRecord singleRequest() {
            assertThat(requests).hasSize(1);
            return requests.get(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(new RequestRecord(
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        requestBody));

                if (exchange.getRequestURI().getPath().equals("/v1/chat/completions")) {
                    if (requestBody.matches("(?s).*\\\"stream\\\"\\s*:\\s*true.*")) {
                        sendStream(exchange);
                    } else {
                        sendJson(exchange, chatResponse());
                    }
                } else if (exchange.getRequestURI().getPath().equals("/v1/embeddings")) {
                    sendJson(exchange, embeddingResponse());
                } else if (exchange.getRequestURI().getPath().equals("/v1/moderations")) {
                    sendJson(exchange, moderationResponse());
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            }
        }

        private static void sendJson(HttpExchange exchange, String body) throws IOException {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        }

        private static void sendStream(HttpExchange exchange) throws IOException {
            String body = asServerSentEvent("""
                    {
                      "id": "stream-response-1",
                      "model": "mistral-stream-model",
                      "choices": [
                        {
                          "index": 0,
                          "delta": {
                            "role": "assistant",
                            "content": [{"type": "text", "text": "Hello "}]
                          }
                        }
                      ]
                    }
                    """) + asServerSentEvent("""
                    {
                      "id": "stream-response-1",
                      "model": "mistral-stream-model",
                      "choices": [
                        {
                          "index": 0,
                          "delta": {
                            "role": "assistant",
                            "content": [{"type": "text", "text": "from Mistral!"}]
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 4,
                        "completion_tokens": 5,
                        "total_tokens": 9
                      }
                    }
                    """) + "data: [DONE]\n\n";
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        }

        private static String asServerSentEvent(String data) {
            return "data: " + data.replace("\n", "\ndata: ") + "\n\n";
        }

        private static String chatResponse() {
            return """
                    {
                      "id": "chat-response-1",
                      "object": "chat.completion",
                      "created": 1720000000,
                      "model": "mistral-response-model",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": [
                              {
                                "type": "thinking",
                                "thinking": [{"type": "text", "text": "I should answer directly."}]
                              },
                              {"type": "text", "text": "Paris is the capital of France."}
                            ],
                            "tool_calls": [
                              {
                                "id": "call-weather-1",
                                "type": "function",
                                "function": {
                                  "name": "weather",
                                  "arguments": "{\\"city\\":\\"Paris\\"}"
                                }
                              }
                            ]
                          },
                          "finish_reason": "tool_calls"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 8,
                        "completion_tokens": 6,
                        "total_tokens": 14
                      }
                    }
                    """;
        }

        private static String embeddingResponse() {
            return """
                    {
                      "id": "embedding-response-1",
                      "object": "list",
                      "model": "mistral-embed-response-model",
                      "data": [
                        {"object": "embedding", "embedding": [0.125, 0.5, -0.25], "index": 0},
                        {"object": "embedding", "embedding": [-0.75, 0.25, 1.0], "index": 1}
                      ],
                      "usage": {
                        "prompt_tokens": 5,
                        "completion_tokens": 0,
                        "total_tokens": 5
                      }
                    }
                    """;
        }

        private static String moderationResponse() {
            return """
                    {
                      "id": "moderation-response-1",
                      "model": "mistral-moderation-response-model",
                      "results": [
                        {
                          "categories": {
                            "sexual": false,
                            "hate_and_discrimination": false,
                            "violence_and_threats": true,
                            "dangerous_and_criminal_content": false,
                            "selfharm": false,
                            "health": false,
                            "law": false,
                            "pii": false
                          },
                          "category_scores": {
                            "sexual": 0.01,
                            "hate_and_discrimination": 0.02,
                            "violence_and_threats": 0.98,
                            "dangerous_and_criminal_content": 0.03,
                            "selfharm": 0.01,
                            "health": 0.01,
                            "law": 0.01,
                            "pii": 0.01
                          }
                        }
                      ]
                    }
                    """;
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(HTTP_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }
}
