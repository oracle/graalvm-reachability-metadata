/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_genai.google_genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ResponseStreamInnerResponseStreamIteratorTest {

    private static final String MODEL = "gemini-stream-test";
    private static final int HTTP_TIMEOUT_MILLIS = 10_000;

    @Test
    @Timeout(55)
    void iteratesResponsesFromStreamingContentApi() throws Exception {
        try (StreamingServer server = StreamingServer.start();
                Client client = Client.builder()
                        .apiKey("test-api-key")
                        .httpOptions(HttpOptions.builder()
                                .baseUrl(server.baseUrl())
                                .timeout(HTTP_TIMEOUT_MILLIS)
                                .build())
                        .build();
                ResponseStream<GenerateContentResponse> stream =
                        client.models.generateContentStream(MODEL, "Stream a greeting", null)) {
            List<String> chunks = new ArrayList<>();
            for (GenerateContentResponse response : stream) {
                chunks.add(response.text());
            }

            assertThat(chunks).containsExactly("Hello ", "from Gemini!");
            assertThat(server.requestMethod()).isEqualTo("POST");
            assertThat(server.requestPath()).isEqualTo("/v1beta/models/" + MODEL + ":streamGenerateContent");
            assertThat(server.requestQuery()).isEqualTo("alt=sse");
            assertThat(server.apiKey()).isEqualTo("test-api-key");
            assertThat(server.requestBody()).contains("Stream a greeting", "contents", "parts");
        }
    }

    private static String responseChunk(String responseId, String text) {
        return """
                {"responseId":"%s","candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"}]}
                """.formatted(responseId, text).trim();
    }

    private static final class StreamingServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private volatile String requestMethod;
        private volatile String requestPath;
        private volatile String requestQuery;
        private volatile String apiKey;
        private volatile String requestBody;

        private StreamingServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static StreamingServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            StreamingServer streamingServer = new StreamingServer(server, executor);
            server.createContext("/", streamingServer::handle);
            server.setExecutor(executor);
            server.start();
            return streamingServer;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        String requestMethod() {
            return requestMethod;
        }

        String requestPath() {
            return requestPath;
        }

        String requestQuery() {
            return requestQuery;
        }

        String apiKey() {
            return apiKey;
        }

        String requestBody() {
            return requestBody;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                requestMethod = exchange.getRequestMethod();
                requestPath = exchange.getRequestURI().getPath();
                requestQuery = exchange.getRequestURI().getQuery();
                apiKey = exchange.getRequestHeaders().getFirst("x-goog-api-key");
                requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                String body = "data: " + responseChunk("chunk-1", "Hello ") + "\n\n"
                        + "data: " + responseChunk("chunk-2", "from Gemini!") + "\n\n";
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
