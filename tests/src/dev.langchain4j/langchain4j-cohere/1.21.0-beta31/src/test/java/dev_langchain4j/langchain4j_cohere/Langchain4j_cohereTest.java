/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_cohere;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.cohere.CohereEmbeddingModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
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

public class Langchain4j_cohereTest {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    @Timeout(55)
    void embedsTextThroughCohereV1Endpoint() throws Exception {
        try (CohereServer server = CohereServer.start()) {
            CohereEmbeddingModel model = CohereEmbeddingModel.builder()
                    .apiKey("test-api-key")
                    .modelName("embed-test-model")
                    .inputType("search_document")
                    .baseUrl(server.v1BaseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .build();

            Response<List<Embedding>> response = model.embedAll(
                    List.of(TextSegment.from("a document to embed"), TextSegment.from("another document")));

            assertThat(response.content()).hasSize(2);
            assertThat(response.content().get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f);
            assertThat(response.content().get(1).vector()).containsExactly(0.4f, 0.5f, 0.6f);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(13);
            assertThat(server.paths()).containsExactly("/v1/embed");
            assertThat(server.requestBodies().get(0))
                    .contains("embed-test-model", "search_document", "a document to embed", "another document");
        }
    }

    @Test
    @Timeout(55)
    void embedsMultimodalInputThroughCohereV2Endpoint() throws Exception {
        try (CohereServer server = CohereServer.start()) {
            CohereEmbeddingModel model = CohereEmbeddingModel.builder()
                    .apiKey("test-api-key")
                    .modelName("embed-v4-test-model")
                    .baseUrl(server.v1BaseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .build();

            EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                    .input(
                            TextContent.from("a caption"),
                            ImageContent.from("https://example.com/test-image.png"),
                            ImageContent.from("aGVsbG8=", "image/png"))
                    .inputType(EmbeddingInputType.QUERY)
                    .build());

            assertThat(response.embeddings()).hasSize(1);
            assertThat(response.embeddings().get(0).vector()).containsExactly(0.7f, 0.8f, 0.9f);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(17);
            assertThat(server.paths()).containsExactly("/v2/embed");
            assertThat(server.requestBodies().get(0))
                    .contains(
                            "embed-v4-test-model",
                            "search_query",
                            "a caption",
                            "image_url",
                            "test-image.png",
                            "data:image/png;base64,aGVsbG8=");
        }
    }

    @Test
    @Timeout(55)
    void batchesV2EmbeddingInputsAccordingToConfiguredLimit() throws Exception {
        try (CohereServer server = CohereServer.start()) {
            CohereEmbeddingModel model = CohereEmbeddingModel.builder()
                    .apiKey("test-api-key")
                    .modelName("embed-batch-test-model")
                    .baseUrl(server.v1BaseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxSegmentsPerBatch(1)
                    .build();

            EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                    .inputs("first batch input", "second batch input")
                    .inputType(EmbeddingInputType.QUERY)
                    .build());

            assertThat(response.embeddings()).hasSize(2);
            assertThat(response.embeddings().get(0).vector()).containsExactly(0.2f, 0.3f, 0.4f);
            assertThat(response.embeddings().get(1).vector()).containsExactly(0.5f, 0.6f, 0.7f);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(12);
            assertThat(server.paths()).containsExactly("/v2/embed", "/v2/embed");
            assertThat(server.requestBodies().get(0)).contains("first batch input");
            assertThat(server.requestBodies().get(1)).contains("second batch input");
        }
    }

    @Test
    @Timeout(55)
    void reranksDocumentsThroughCohereEndpoint() throws Exception {
        try (CohereServer server = CohereServer.start()) {
            CohereScoringModel model = CohereScoringModel.builder()
                    .apiKey("test-api-key")
                    .modelName("rerank-test-model")
                    .baseUrl(server.v1BaseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            Response<List<Double>> response = model.scoreAll(
                    List.of(TextSegment.from("first document"), TextSegment.from("second document")),
                    "the relevant query");

            assertThat(response.content()).containsExactly(0.35, 0.85);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(4);
            assertThat(server.paths()).containsExactly("/v1/rerank");
            assertThat(server.requestBodies().get(0))
                    .contains("rerank-test-model", "the relevant query", "first document", "second document");
        }
    }

    @Test
    @Timeout(55)
    void reranksDocumentsAsynchronouslyThroughCohereEndpoint() throws Exception {
        try (CohereServer server = CohereServer.start()) {
            CohereScoringModel model = CohereScoringModel.builder()
                    .apiKey("test-api-key")
                    .modelName("rerank-async-test-model")
                    .baseUrl(server.v1BaseUrl())
                    .timeout(HTTP_TIMEOUT)
                    .maxRetries(0)
                    .build();

            ScoringResponse response = model.scoreAsync(ScoringRequest.builder()
                            .documents(List.of("first async document", "second async document"))
                            .query("the relevant async query")
                            .build())
                    .get(30, TimeUnit.SECONDS);

            assertThat(response.scores()).containsExactly(0.35, 0.85);
            assertThat(response.modelName()).isEqualTo("rerank-async-test-model");
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(4);
            assertThat(server.paths()).containsExactly("/v1/rerank");
            assertThat(server.requestBodies().get(0))
                    .contains(
                            "rerank-async-test-model",
                            "the relevant async query",
                            "first async document",
                            "second async document");
        }
    }

    private static final class CohereServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final CopyOnWriteArrayList<String> paths = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();

        private CohereServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static CohereServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CohereServer cohereServer = new CohereServer(server, executor);
            server.createContext("/", cohereServer::handle);
            server.setExecutor(executor);
            server.start();
            return cohereServer;
        }

        String v1BaseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        }

        List<String> paths() {
            return List.copyOf(paths);
        }

        List<String> requestBodies() {
            return List.copyOf(requestBodies);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                paths.add(path);
                requestBodies.add(requestBody);

                String responseBody;
                if ("/v1/embed".equals(path)) {
                    responseBody = legacyEmbeddingResponse();
                } else if ("/v2/embed".equals(path)) {
                    responseBody = embeddingV2Response(requestBody);
                } else if ("/v1/rerank".equals(path)) {
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

        private static String legacyEmbeddingResponse() {
            return """
                    {
                      "id": "legacy-embedding",
                      "texts": ["a document to embed", "another document"],
                      "embeddings": [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]],
                      "meta": {"billed_units": {"input_tokens": 13, "output_tokens": 0, "search_units": 0}}
                    }
                    """.trim();
        }

        private static String embeddingV2Response(String requestBody) {
            if (requestBody.contains("first batch input")) {
                return firstBatchedEmbeddingResponse();
            }
            if (requestBody.contains("second batch input")) {
                return secondBatchedEmbeddingResponse();
            }
            return multimodalEmbeddingResponse();
        }

        private static String multimodalEmbeddingResponse() {
            return """
                    {
                      "id": "multimodal-embedding",
                      "embeddings": {"float": [[0.7, 0.8, 0.9]]},
                      "meta": {"billed_units": {"input_tokens": 17, "output_tokens": 0, "search_units": 0}}
                    }
                    """.trim();
        }

        private static String firstBatchedEmbeddingResponse() {
            return """
                    {
                      "id": "first-batch-embedding",
                      "embeddings": {"float": [[0.2, 0.3, 0.4]]},
                      "meta": {"billed_units": {"input_tokens": 5, "output_tokens": 0, "search_units": 0}}
                    }
                    """.trim();
        }

        private static String secondBatchedEmbeddingResponse() {
            return """
                    {
                      "id": "second-batch-embedding",
                      "embeddings": {"float": [[0.5, 0.6, 0.7]]},
                      "meta": {"billed_units": {"input_tokens": 7, "output_tokens": 0, "search_units": 0}}
                    }
                    """.trim();
        }

        private static String rerankResponse() {
            return """
                    {
                      "results": [
                        {"index": 1, "relevance_score": 0.85},
                        {"index": 0, "relevance_score": 0.35}
                      ],
                      "meta": {"billed_units": {"input_tokens": 0, "output_tokens": 0, "search_units": 4}}
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
