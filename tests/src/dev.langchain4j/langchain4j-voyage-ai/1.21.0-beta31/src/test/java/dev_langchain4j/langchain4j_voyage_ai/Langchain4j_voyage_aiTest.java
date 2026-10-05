/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_voyage_ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel;
import dev.langchain4j.model.voyageai.VoyageAiScoringModel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Langchain4j_voyage_aiTest {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String MODEL_NAME = "voyage-test-model";
    private static final String MULTIMODAL_MODEL_NAME = "voyage-multimodal-3.5";

    @Test
    @Timeout(55)
    void embedsTextAndMultipleSegmentsFromLocalVoyageResponses() throws Exception {
        try (VoyageServer server = VoyageServer.start()) {
            VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
                    .apiKey("test-api-key")
                    .modelName(MODEL_NAME)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            Response<Embedding> singleResponse = model.embed("first voyage input");
            assertThat(singleResponse.content().vector()).containsExactly(0.1f, 0.2f, 0.3f);
            assertThat(singleResponse.tokenUsage().totalTokenCount()).isEqualTo(7);

            Response<List<Embedding>> batchResponse = model.embedAll(List.of(
                    TextSegment.from("first voyage input"), TextSegment.from("second voyage input")));
            assertThat(batchResponse.content()).hasSize(2);
            assertThat(batchResponse.content().get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f);
            assertThat(batchResponse.content().get(1).vector()).containsExactly(0.4f, 0.5f, 0.6f);
            assertThat(batchResponse.tokenUsage().totalTokenCount()).isEqualTo(11);

            List<String> requestBodies = server.requestBodies();
            assertThat(requestBodies).hasSize(2);
            assertThat(requestBodies.get(0)).contains(MODEL_NAME, "first voyage input");
            assertThat(requestBodies.get(1)).contains(MODEL_NAME, "first voyage input", "second voyage input");
        }
    }

    @Test
    @Timeout(55)
    void embedsInterleavedTextAndImageFromLocalVoyageResponse() throws Exception {
        try (VoyageServer server = VoyageServer.start()) {
            VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
                    .apiKey("test-api-key")
                    .modelName(MULTIMODAL_MODEL_NAME)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                    .input(
                            TextContent.from("a voyage image caption"),
                            ImageContent.from("https://example.com/voyage-image.png"))
                    .build());

            assertThat(response.embeddings()).hasSize(1);
            assertThat(response.embeddings().get(0).vector()).containsExactly(0.7f, 0.8f, 0.9f);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(17);

            List<String> requestBodies = server.requestBodies();
            assertThat(requestBodies).hasSize(1);
            assertThat(requestBodies.get(0))
                    .contains(MULTIMODAL_MODEL_NAME, "a voyage image caption", "image_url", "voyage-image.png");
        }
    }

    @Test
    @Timeout(55)
    void scoresAllSegmentsFromLocalVoyageResponse() throws Exception {
        try (VoyageServer server = VoyageServer.start()) {
            VoyageAiScoringModel model = VoyageAiScoringModel.builder()
                    .apiKey("test-api-key")
                    .modelName(MODEL_NAME)
                    .baseUrl(server.baseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            Response<List<Double>> response = model.scoreAll(
                    List.of(TextSegment.from("first document"), TextSegment.from("second document")),
                    "voyage query");

            assertThat(response.content()).containsExactly(0.4, 0.9);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(13);

            List<String> requestBodies = server.requestBodies();
            assertThat(requestBodies).hasSize(1);
            assertThat(requestBodies.get(0)).contains(MODEL_NAME, "first document", "second document", "voyage query");
        }
    }

    private static final class VoyageServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();

        private VoyageServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static VoyageServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            VoyageServer voyageServer = new VoyageServer(server, executor);
            server.createContext("/", voyageServer::handle);
            server.setExecutor(executor);
            server.start();
            return voyageServer;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<String> requestBodies() {
            return List.copyOf(requestBodies);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requestBodies.add(requestBody);
                String responseBody;
                if ("/embeddings".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                    responseBody = requestBody.contains("second voyage input")
                            ? batchEmbeddingResponse()
                            : singleEmbeddingResponse();
                } else if ("/multimodalembeddings".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                    responseBody = multimodalEmbeddingResponse();
                } else if ("/rerank".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                    responseBody = rerankResponse();
                } else {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        }

        private static String singleEmbeddingResponse() {
            return """
                    {
                      "object": "list",
                      "data": [{"object": "embedding", "embedding": [0.1, 0.2, 0.3], "index": 0}],
                      "model": "voyage-test-model",
                      "usage": {"total_tokens": 7}
                    }
                    """.trim();
        }

        private static String batchEmbeddingResponse() {
            return """
                    {
                      "object": "list",
                      "data": [
                        {"object": "embedding", "embedding": [0.1, 0.2, 0.3], "index": 0},
                        {"object": "embedding", "embedding": [0.4, 0.5, 0.6], "index": 1}
                      ],
                      "model": "voyage-test-model",
                      "usage": {"total_tokens": 11}
                    }
                    """.trim();
        }

        private static String multimodalEmbeddingResponse() {
            return """
                    {
                      "object": "list",
                      "data": [{"object": "embedding", "embedding": [0.7, 0.8, 0.9], "index": 0}],
                      "model": "voyage-multimodal-3.5",
                      "usage": {"total_tokens": 17}
                    }
                    """.trim();
        }

        private static String rerankResponse() {
            return """
                    {
                      "object": "list",
                      "data": [
                        {"object": "search_result", "relevance_score": 0.9, "index": 1},
                        {"object": "search_result", "relevance_score": 0.4, "index": 0}
                      ],
                      "model": "voyage-test-model",
                      "usage": {"total_tokens": 13}
                    }
                    """.trim();
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(HTTP_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        }
    }
}
