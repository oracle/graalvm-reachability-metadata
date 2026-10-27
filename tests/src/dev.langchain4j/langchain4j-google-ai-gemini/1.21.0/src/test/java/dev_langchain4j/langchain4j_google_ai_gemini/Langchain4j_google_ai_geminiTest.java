/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_google_ai_gemini;

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
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatResponseMetadata;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiTokenCountEstimator;
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

public class Langchain4j_google_ai_geminiTest {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String CHAT_MODEL = "gemini-test-chat";
    private static final String EMBEDDING_MODEL = "gemini-test-embedding";

    @Test
    @Timeout(55)
    void chatsAgainstLocalGeminiEndpoint() throws Exception {
        try (GeminiServer server = GeminiServer.start()) {
            GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(CHAT_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            ChatResponse response = model.chat(UserMessage.from("Which planet is known as the red planet?"));

            assertThat(response.aiMessage().text()).isEqualTo("Mars is known as the red planet.");
            assertThat(response.id()).isEqualTo("response-chat");
            assertThat(response.modelName()).isEqualTo("gemini-test-chat-001");
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(7);

            GoogleAiGeminiChatResponseMetadata metadata =
                    (GoogleAiGeminiChatResponseMetadata) response.metadata();
            assertThat(metadata.safetyRatings()).singleElement().satisfies(rating -> {
                assertThat(rating.category()).isEqualTo("HARM_CATEGORY_HARASSMENT");
                assertThat(rating.probability()).isEqualTo("NEGLIGIBLE");
                assertThat(rating.blocked()).isFalse();
            });
            assertThat(metadata.groundingMetadata().webSearchQueries()).containsExactly("red planet");
            assertThat(metadata.groundingMetadata().groundingChunks().get(0).web().uri())
                    .isEqualTo("https://example.test/mars");
            assertThat(metadata.groundingMetadata().groundingChunks().get(1).maps().placeAnswerSources()
                            .reviewSnippets()
                            .get(0)
                            .reviewId())
                    .isEqualTo("review-1");
            assertThat(metadata.groundingMetadata().groundingSupports().get(0).segment().text())
                    .isEqualTo("Mars");
            assertThat(metadata.urlContextMetadata().urlMetadata().get(0).retrievedUrl())
                    .isEqualTo("https://example.test/mars");
            assertThat(metadata.urlContextMetadata().urlMetadata().get(0).urlRetrievalStatus())
                    .isEqualTo("URL_RETRIEVAL_STATUS_SUCCESS");

            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/models/" + CHAT_MODEL + ":generateContent");
            assertThat(request.apiKey()).isEqualTo("test-api-key");
            assertThat(request.body()).contains("Which planet is known as the red planet?", "candidateCount");
        }
    }

    @Test
    @Timeout(55)
    void mapsBlockedPromptFeedbackFromLocalGeminiEndpoint() throws Exception {
        try (GeminiServer server = GeminiServer.start()) {
            GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey("test-api-key")
                    .modelName(CHAT_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            ChatResponse response = model.chat(UserMessage.from("Return a blocked prompt response"));

            GoogleAiGeminiChatResponseMetadata metadata =
                    (GoogleAiGeminiChatResponseMetadata) response.metadata();
            assertThat(response.finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
            assertThat(metadata.blockReason()).isEqualTo("SAFETY");
            assertThat(metadata.promptSafetyRatings()).singleElement().satisfies(rating -> {
                assertThat(rating.category()).isEqualTo("HARM_CATEGORY_DANGEROUS_CONTENT");
                assertThat(rating.probability()).isEqualTo("HIGH");
                assertThat(rating.blocked()).isTrue();
            });
            assertThat(server.singleRequest().path()).isEqualTo("/models/" + CHAT_MODEL + ":generateContent");
        }
    }

    @Test
    @Timeout(55)
    void streamsChatAgainstLocalGeminiEndpoint() throws Exception {
        try (GeminiServer server = GeminiServer.start()) {
            GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("stream-api-key")
                    .modelName(CHAT_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .build();
            CountDownLatch completed = new CountDownLatch(1);
            List<String> partialResponses = new CopyOnWriteArrayList<>();
            AtomicReference<ChatResponse> completeResponse = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            model.chat("Stream a short greeting", new StreamingChatResponseHandler() {
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
            assertThat(partialResponses).containsExactly("Hello ", "from Gemini!");
            assertThat(completeResponse.get().aiMessage().text()).isEqualTo("Hello from Gemini!");
            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/models/" + CHAT_MODEL + ":streamGenerateContent");
            assertThat(request.query()).isEqualTo("alt=sse");
            assertThat(request.body()).contains("Stream a short greeting");
        }
    }

    @Test
    @Timeout(55)
    void countsTokensAgainstLocalGeminiEndpoint() throws Exception {
        try (GeminiServer server = GeminiServer.start()) {
            GoogleAiGeminiTokenCountEstimator estimator = GoogleAiGeminiTokenCountEstimator.builder()
                    .apiKey("token-api-key")
                    .modelName(CHAT_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            int tokenCount = estimator.estimateTokenCountInText("Count the tokens in this sentence");

            assertThat(tokenCount).isEqualTo(6);
            RequestRecord request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/models/" + CHAT_MODEL + ":countTokens");
            assertThat(request.apiKey()).isEqualTo("token-api-key");
            assertThat(request.body()).contains("Count the tokens in this sentence", "contents", "parts");
        }
    }

    @Test
    @Timeout(55)
    void createsEmbeddingsFromLocalGeminiEndpoint() throws Exception {
        try (GeminiServer server = GeminiServer.start()) {
            GoogleAiEmbeddingModel model = GoogleAiEmbeddingModel.builder()
                    .apiKey("embedding-api-key")
                    .modelName(EMBEDDING_MODEL)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .outputDimensionality(3)
                    .build();

            Response<Embedding> response = model.embed("Embed this Gemini sentence");
            Response<List<Embedding>> batchResponse = model.embedAll(
                    List.of(TextSegment.from("First batch sentence"), TextSegment.from("Second batch sentence")));

            assertThat(response.content().vector()).containsExactly(0.125f, 0.5f, -0.25f);
            assertThat(batchResponse.content()).hasSize(2);
            assertThat(batchResponse.content().get(0).vector()).containsExactly(0.25f, 0.5f, 0.75f);
            assertThat(batchResponse.content().get(1).vector()).containsExactly(-0.5f, 0.0f, 0.5f);

            assertThat(server.requests()).hasSize(2);
            RequestRecord singleRequest = server.requests().get(0);
            assertThat(singleRequest.path()).isEqualTo("/models/" + EMBEDDING_MODEL + ":embedContent");
            assertThat(singleRequest.apiKey()).isEqualTo("embedding-api-key");
            assertThat(singleRequest.body())
                    .contains("models/" + EMBEDDING_MODEL, "Embed this Gemini sentence", "outputDimensionality", "3");
            RequestRecord batchRequest = server.requests().get(1);
            assertThat(batchRequest.path()).isEqualTo("/models/" + EMBEDDING_MODEL + ":batchEmbedContents");
            assertThat(batchRequest.body()).contains("First batch sentence", "Second batch sentence", "requests");
        }
    }

    private record RequestRecord(String path, String query, String apiKey, String body) {}

    private static final class GeminiServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final CopyOnWriteArrayList<RequestRecord> requests = new CopyOnWriteArrayList<>();

        private GeminiServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static GeminiServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            GeminiServer geminiServer = new GeminiServer(server, executor);
            server.createContext("/", geminiServer::handle);
            server.setExecutor(executor);
            server.start();
            return geminiServer;
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
                requests.add(new RequestRecord(
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getQuery(),
                        exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                        requestBody));

                String path = exchange.getRequestURI().getPath();
                if (path.endsWith(":generateContent")) {
                    if (requestBody.contains("Return a blocked prompt response")) {
                        sendJson(exchange, blockedPromptResponse());
                    } else {
                        sendJson(exchange, chatResponse("Mars is known as the red planet.", "response-chat"));
                    }
                } else if (path.endsWith(":streamGenerateContent")) {
                    sendStream(exchange);
                } else if (path.endsWith(":countTokens")) {
                    sendJson(exchange, """
                            {
                              "totalTokens": 6
                            }
                            """);
                } else if (path.endsWith(":embedContent")) {
                    sendJson(exchange, """
                            {
                              "embedding": {
                                "values": [0.125, 0.5, -0.25]
                              }
                            }
                            """);
                } else if (path.endsWith(":batchEmbedContents")) {
                    sendJson(exchange, """
                            {
                              "embeddings": [
                                {"values": [0.25, 0.5, 0.75]},
                                {"values": [-0.5, 0.0, 0.5]}
                              ]
                            }
                            """);
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
            String body = asServerSentEvent(chatResponse("Hello ", "response-stream-1"))
                    + asServerSentEvent(chatResponse("from Gemini!", "response-stream-2"));
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        }

        private static String asServerSentEvent(String data) {
            return "data: " + data.replace("\n", "\ndata: ") + "\n\n";
        }

        private static String chatResponse(String text, String responseId) {
            return """
                    {
                      "responseId": "%s",
                      "modelVersion": "gemini-test-chat-001",
                      "candidates": [
                        {
                          "content": {
                            "parts": [{"text": "%s"}],
                            "role": "model"
                          },
                          "finishReason": "STOP",
                          "safetyRatings": [
                            {
                              "category": "HARM_CATEGORY_HARASSMENT",
                              "probability": "NEGLIGIBLE",
                              "blocked": false
                            }
                          ],
                          "urlContextMetadata": {
                            "urlMetadata": [
                              {
                                "retrievedUrl": "https://example.test/mars",
                                "urlRetrievalStatus": "URL_RETRIEVAL_STATUS_SUCCESS"
                              }
                            ]
                          }
                        }
                      ],
                      "usageMetadata": {
                        "promptTokenCount": 4,
                        "candidatesTokenCount": 3,
                        "totalTokenCount": 7,
                        "cachedContentTokenCount": 0,
                        "thoughtsTokenCount": 0
                      },
                      "groundingMetadata": {
                        "groundingChunks": [
                          {"web": {"uri": "https://example.test/mars", "title": "Mars reference"}},
                          {
                            "maps": {
                              "uri": "https://maps.example.test/mars",
                              "title": "Mars exhibit",
                              "text": "A planetary exhibit",
                              "placeId": "place-1",
                              "placeAnswerSources": {
                                "reviewSnippets": [
                                  {
                                    "reviewId": "review-1",
                                    "googleMapsUri": "https://maps.example.test/review-1",
                                    "title": "Planetarium review"
                                  }
                                ]
                              }
                            }
                          }
                        ],
                        "groundingSupports": [
                          {
                            "groundingChunkIndices": [0],
                            "confidenceScores": [0.99],
                            "segment": {"partIndex": 0, "startIndex": 0, "endIndex": 4, "text": "Mars"}
                          }
                        ],
                        "webSearchQueries": ["red planet"],
                        "searchEntryPoint": {"renderedContent": "Mars results", "sdkBlob": "sdk-data"},
                        "retrievalMetadata": {"googleSearchDynamicRetrievalScore": 0.98},
                        "googleMapsWidgetContextToken": "maps-token"
                      }
                    }
                    """.formatted(responseId, text).trim();
        }

        private static String blockedPromptResponse() {
            return """
                    {
                      "responseId": "response-blocked",
                      "modelVersion": "gemini-test-chat-001",
                      "candidates": [],
                      "promptFeedback": {
                        "blockReason": "SAFETY",
                        "safetyRatings": [
                          {
                            "category": "HARM_CATEGORY_DANGEROUS_CONTENT",
                            "probability": "HIGH",
                            "blocked": true
                          }
                        ]
                      },
                      "usageMetadata": {
                        "promptTokenCount": 5,
                        "candidatesTokenCount": 0,
                        "totalTokenCount": 5,
                        "cachedContentTokenCount": 0,
                        "thoughtsTokenCount": 0
                      }
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
