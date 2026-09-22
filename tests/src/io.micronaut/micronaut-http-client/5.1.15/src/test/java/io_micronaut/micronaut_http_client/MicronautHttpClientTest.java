/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MicronautHttpClientTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    @Timeout(55)
    void followsRelativeRedirectsAndDecompressesGzipResponses() throws Exception {
        try (TestServer server = TestServer.start();
                HttpClient client = HttpClient.create(server.baseUri().toURL(), clientConfiguration())) {
            HttpRequest<String> request = HttpRequest.POST("/redirect", "discarded on redirect")
                    .header("X-Trace-Id", "trace-19")
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE);

            HttpResponse<String> response = client.toBlocking().exchange(request, String.class);

            assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(response.body()).isEqualTo("GET|source=redirect|trace-19|compressed response");
        }
    }

    @Test
    @Timeout(55)
    void encodesMultipartFieldsAndFilesAndReadsAChunkedResponse() throws Exception {
        byte[] fileContents = "native client upload".getBytes(StandardCharsets.UTF_8);
        MultipartBody body = MultipartBody.builder()
                .addPart("description", "release notes")
                .addPart("document", "notes.txt", MediaType.TEXT_PLAIN_TYPE, fileContents)
                .build();

        try (TestServer server = TestServer.start();
                HttpClient client = HttpClient.create(server.baseUri().toURL(), clientConfiguration())) {
            HttpRequest<?> request = HttpRequest.POST("/multipart", body)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE);

            HttpResponse<String> response = client.toBlocking().exchange(request, String.class);

            assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
            assertThat(response.body())
                    .isEqualTo("multipart|description|release notes|document|notes.txt|native client upload");
            assertThat(response.getHeaders().get("X-Transfer-Mode")).isEqualTo("chunked");
        }
    }

    @Test
    @Timeout(55)
    void exposesStatusHeadersAndBodyForErrorResponses() throws Exception {
        try (TestServer server = TestServer.start();
                HttpClient client = HttpClient.create(server.baseUri().toURL(), clientConfiguration())) {
            BlockingHttpClient blockingClient = client.toBlocking();

            HttpClientResponseException exception = assertThrows(
                    HttpClientResponseException.class,
                    () -> blockingClient.exchange(HttpRequest.GET("/error"), String.class));

            assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
            assertThat(exception.getResponse().getHeaders().get("X-Error-Code")).isEqualTo("invalid-widget");
            assertThat(exception.getResponse().getBody(String.class)).contains("widget id is required");
        }
    }

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(HTTP_TIMEOUT);
        configuration.setReadTimeout(HTTP_TIMEOUT);
        configuration.setRequestTimeout(HTTP_TIMEOUT);
        return configuration;
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            server.setExecutor(executor);
            server.createContext("/redirect", TestServer::redirect);
            server.createContext("/compressed", TestServer::compressed);
            server.createContext("/multipart", TestServer::multipart);
            server.createContext("/error", TestServer::error);
            server.start();
            return new TestServer(server, executor);
        }

        URI baseUri() {
            String host = server.getAddress().getAddress().getHostAddress();
            String uriHost = host.contains(":") ? "[" + host + "]" : host;
            return URI.create("http://" + uriHost + ":" + server.getAddress().getPort());
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }

        private static void redirect(HttpExchange exchange) throws IOException {
            try (exchange) {
                exchange.getResponseHeaders().set("Location", "/compressed?source=redirect");
                exchange.sendResponseHeaders(303, -1);
            }
        }

        private static void compressed(HttpExchange exchange) throws IOException {
            try (exchange) {
                String response = String.join(
                        "|",
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getRawQuery(),
                        exchange.getRequestHeaders().getFirst("X-Trace-Id"),
                        "compressed response");
                byte[] compressed = gzip(response);
                exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_PLAIN);
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(HttpStatus.OK.getCode(), compressed.length);
                exchange.getResponseBody().write(compressed);
            }
        }

        private static void multipart(HttpExchange exchange) throws IOException {
            try (exchange) {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String response = String.join(
                        "|",
                        contentType != null && contentType.startsWith("multipart/form-data;")
                                ? "multipart"
                                : "unexpected-content-type",
                        requestBody.contains("name=\"description\"") ? "description" : "missing-description",
                        requestBody.contains("release notes") ? "release notes" : "missing-description-value",
                        requestBody.contains("name=\"document\"") ? "document" : "missing-document",
                        requestBody.contains("filename=\"notes.txt\"") ? "notes.txt" : "missing-filename",
                        requestBody.contains("native client upload")
                                ? "native client upload"
                                : "missing-file-contents");
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_PLAIN);
                exchange.getResponseHeaders().set("X-Transfer-Mode", "chunked");
                exchange.sendResponseHeaders(HttpStatus.OK.getCode(), 0);
                exchange.getResponseBody().write(responseBytes);
            }
        }

        private static void error(HttpExchange exchange) throws IOException {
            try (exchange) {
                byte[] response = "widget id is required".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_PLAIN);
                exchange.getResponseHeaders().set("X-Error-Code", "invalid-widget");
                exchange.sendResponseHeaders(HttpStatus.BAD_REQUEST.getCode(), response.length);
                exchange.getResponseBody().write(response);
            }
        }
    }
}
