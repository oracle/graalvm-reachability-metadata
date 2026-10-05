/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_jina;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.jina.JinaEmbeddingModel;
import dev.langchain4j.model.jina.JinaScoringModel;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class Langchain4j_jinaTest {

    @Test
    void textEmbeddingMapsRequestAndResponse() throws Exception {
        try (TestHttpServer server = TestHttpServer.start("""
                {
                  "model": "jina-embeddings-v3",
                  "data": [
                    {"index": 0, "object": "embedding", "embedding": [0.1, 0.2]},
                    {"index": 1, "object": "embedding", "embedding": [0.3, 0.4]}
                  ],
                  "usage": {"prompt_tokens": 7, "total_tokens": 9}
                }
                """)) {
            JinaEmbeddingModel model = JinaEmbeddingModel.builder()
                    .baseUrl(server.url())
                    .apiKey("test-key")
                    .modelName("jina-embeddings-v3")
                    .lateChunking(true)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                    .input("first document")
                    .input("second document")
                    .inputType(EmbeddingInputType.QUERY)
                    .build());

            assertThat(server.requestMethod()).isEqualTo("POST");
            assertThat(server.requestPath()).isEqualTo("/v1/embeddings");
            assertThat(server.requestHeader("Authorization")).isEqualTo("Bearer test-key");
            assertThat(server.requestBody())
                    .contains("\"model\" : \"jina-embeddings-v3\"")
                    .contains("\"task\" : \"retrieval.query\"")
                    .contains("\"late_chunking\" : true")
                    .contains("first document")
                    .contains("second document");
            assertThat(response.modelName()).isEqualTo("jina-embeddings-v3");
            assertThat(response.embeddings()).hasSize(2);
            assertThat(response.embeddings().get(0).vector()).containsExactly(0.1f, 0.2f);
            assertThat(response.embeddings().get(1).vector()).containsExactly(0.3f, 0.4f);
            assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(7);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(9);
        }
    }

    @Test
    void embeddingRetriesAfterTransientServerFailure() throws Exception {
        try (TestHttpServer server = TestHttpServer.start(1, """
                {
                  "model": "jina-embeddings-v3",
                  "data": [
                    {"index": 0, "object": "embedding", "embedding": [0.9, 0.8]}
                  ],
                  "usage": {"prompt_tokens": 3, "total_tokens": 4}
                }
                """)) {
            JinaEmbeddingModel model = JinaEmbeddingModel.builder()
                    .baseUrl(server.url())
                    .apiKey("test-key")
                    .modelName("jina-embeddings-v3")
                    .maxRetries(1)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                    .input("retry document")
                    .build());

            assertThat(server.requestCount()).isEqualTo(2);
            assertThat(response.embeddings()).hasSize(1);
            assertThat(response.embeddings().get(0).vector()).containsExactly(0.9f, 0.8f);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(4);
        }
    }

    @Test
    void multimodalEmbeddingMapsTextAndImageInputs() throws Exception {
        try (TestHttpServer server = TestHttpServer.start("""
                {
                  "model": "jina-embeddings-v4",
                  "data": [
                    {"index": 0, "object": "embedding", "embedding": [0.5, 0.6]},
                    {"index": 1, "object": "embedding", "embedding": [0.7, 0.8]}
                  ],
                  "usage": {"prompt_tokens": 11, "total_tokens": 13}
                }
                """)) {
            JinaEmbeddingModel model = JinaEmbeddingModel.builder()
                    .baseUrl(server.url())
                    .apiKey("test-key")
                    .modelName("jina-embeddings-v4")
                    .lateChunking(true)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                    .input("a text input")
                    .input(ImageContent.from("https://example.test/image.png"))
                    .inputType(EmbeddingInputType.DOCUMENT)
                    .build());

            assertThat(server.requestPath()).isEqualTo("/v1/embeddings");
            assertThat(server.requestBody())
                    .contains("\"model\" : \"jina-embeddings-v4\"")
                    .contains("\"task\" : \"retrieval.passage\"")
                    .contains("\"late_chunking\" : true")
                    .contains("\"text\" : \"a text input\"")
                    .contains("\"image\" : \"https://example.test/image.png\"");
            assertThat(response.embeddings()).hasSize(2);
            assertThat(response.embeddings().get(0).vector()).containsExactly(0.5f, 0.6f);
            assertThat(response.embeddings().get(1).vector()).containsExactly(0.7f, 0.8f);
            assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(11);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(13);
        }
    }

    @Test
    void scoringMapsDocumentsAndSortsReturnedScores() throws Exception {
        try (TestHttpServer server = TestHttpServer.start("""
                {
                  "model": "jina-reranker-v3",
                  "results": [
                    {"index": 1, "document": {"text": "second document"}, "relevance_score": 0.2},
                    {"index": 0, "document": {"text": "first document"}, "relevance_score": 0.8}
                  ],
                  "usage": {"prompt_tokens": 5, "total_tokens": 6}
                }
                """)) {
            JinaScoringModel model = JinaScoringModel.builder()
                    .baseUrl(server.url())
                    .apiKey("test-key")
                    .modelName("jina-reranker-v3")
                    .timeout(Duration.ofSeconds(10))
                    .build();

            Response<List<Double>> response = model.scoreAll(
                    List.of(TextSegment.from("first document"), TextSegment.from("second document")), "find first");

            assertThat(server.requestMethod()).isEqualTo("POST");
            assertThat(server.requestPath()).isEqualTo("/rerank");
            assertThat(server.requestHeader("Authorization")).isEqualTo("Bearer test-key");
            assertThat(server.requestBody())
                    .contains("\"model\" : \"jina-reranker-v3\"")
                    .contains("\"query\" : \"find first\"")
                    .contains("\"documents\" : [ \"first document\", \"second document\" ]")
                    .contains("\"return_documents\" : false");
            assertThat(response.content()).containsExactly(0.8, 0.2);
            assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(5);
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(6);
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final int transientFailureCount;
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicReference<String> requestMethod = new AtomicReference<>();
        private final AtomicReference<String> requestPath = new AtomicReference<>();
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();

        private TestHttpServer(HttpServer server, ExecutorService executor, int transientFailureCount) {
            this.server = server;
            this.executor = executor;
            this.transientFailureCount = transientFailureCount;
        }

        private static TestHttpServer start(String responseBody) throws IOException {
            return start(0, responseBody);
        }

        private static TestHttpServer start(int transientFailureCount, String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "langchain4j-jina-test-server");
                thread.setDaemon(true);
                return thread;
            });
            TestHttpServer testServer = new TestHttpServer(server, executor, transientFailureCount);
            server.createContext("/", exchange -> testServer.handle(exchange, responseBody));
            server.setExecutor(executor);
            server.start();
            return testServer;
        }

        private void handle(HttpExchange exchange, String responseBody) throws IOException {
            int requestNumber = requestCount.incrementAndGet();
            requestMethod.set(exchange.getRequestMethod());
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try (InputStream input = exchange.getRequestBody()) {
                requestBody.set(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }

            int statusCode = requestNumber <= transientFailureCount ? 500 : 200;
            byte[] response = (statusCode == 200 ? responseBody : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            } finally {
                exchange.close();
            }
        }

        private String url() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        }

        private int requestCount() {
            return requestCount.get();
        }

        private String requestMethod() {
            return requestMethod.get();
        }

        private String requestPath() {
            return requestPath.get();
        }

        private String requestHeader(String name) {
            return "Authorization".equals(name) ? authorization.get() : null;
        }

        private String requestBody() {
            return requestBody.get();
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
