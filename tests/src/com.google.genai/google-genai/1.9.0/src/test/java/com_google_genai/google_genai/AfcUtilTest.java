/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_genai.google_genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Tool;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AfcUtilTest {

    private static final String MODEL = "gemini-afc-test";
    private static final int HTTP_TIMEOUT_MILLIS = 10_000;
    private static final AtomicInteger FUNCTION_CALLS = new AtomicInteger();

    @Test
    @Timeout(55)
    void invokesCallableToolAndReturnsFinalModelResponse() throws Exception {
        FUNCTION_CALLS.set(0);
        Method weatherMethod = AfcUtilTest.class.getMethod("lookupWeather");
        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(Tool.builder().functions(weatherMethod))
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().maximumRemoteCalls(2))
                .build();

        try (FunctionCallingServer server = FunctionCallingServer.start();
                Client client = Client.builder()
                        .apiKey("test-api-key")
                        .httpOptions(HttpOptions.builder()
                                .baseUrl(server.baseUrl())
                                .timeout(HTTP_TIMEOUT_MILLIS)
                                .build())
                        .build()) {
            GenerateContentResponse response =
                    client.models.generateContent(MODEL, "What is the weather?", config);

            assertThat(response.text()).isEqualTo("The weather is sunny.");
            assertThat(response.automaticFunctionCallingHistory()).isPresent();
            assertThat(response.automaticFunctionCallingHistory().orElseThrow()).hasSize(3);
            assertThat(FUNCTION_CALLS).hasValue(1);
            assertThat(server.requests()).hasSize(2);
            assertThat(server.requests()).allSatisfy(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/v1beta/models/" + MODEL + ":generateContent");
                assertThat(request.apiKey()).isEqualTo("test-api-key");
            });
            assertThat(server.requests().get(0).body())
                    .contains("What is the weather?", "functionDeclarations", "lookupWeather");
            assertThat(server.requests().get(1).body())
                    .contains("functionResponse", "lookupWeather", "sunny");
        }
    }

    public static String lookupWeather() {
        FUNCTION_CALLS.incrementAndGet();
        return "sunny";
    }

    private record Request(String method, String path, String apiKey, String body) {}

    private static final class FunctionCallingServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final List<Request> requests = new CopyOnWriteArrayList<>();

        private FunctionCallingServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static FunctionCallingServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            FunctionCallingServer functionCallingServer = new FunctionCallingServer(server, executor);
            server.createContext("/", functionCallingServer::handle);
            server.setExecutor(executor);
            server.start();
            return functionCallingServer;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<Request> requests() {
            return requests;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String requestBody =
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(new Request(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                        requestBody));

                String responseBody = requests.size() == 1 ? functionCallResponse() : finalResponse();
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        }

        private static String functionCallResponse() {
            return """
                    {
                      "responseId": "function-call-response",
                      "candidates": [{
                        "content": {
                          "role": "model",
                          "parts": [{
                            "functionCall": {
                              "name": "lookupWeather",
                              "args": {}
                            }
                          }]
                        },
                        "finishReason": "STOP"
                      }]
                    }
                    """;
        }

        private static String finalResponse() {
            return """
                    {
                      "responseId": "final-response",
                      "candidates": [{
                        "content": {
                          "role": "model",
                          "parts": [{"text": "The weather is sunny."}]
                        },
                        "finishReason": "STOP"
                      }]
                    }
                    """;
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
