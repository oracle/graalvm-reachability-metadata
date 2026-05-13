/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.shared.http.HttpConfiguration;
import org.apache.maven.wagon.shared.http.HttpMethodConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
public class Wagon_httpTest {
    private static final int SHORT_TIMEOUT_MILLIS = 2_000;

    @TempDir
    Path tempDir;

    @Test
    void getFileListFetchesDirectoryAndParsesAcceptableLinks() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/repository/releases/");
            assertThat(exchange.getRequestHeaders().getFirst("Cache-control")).isEqualTo("no-cache");
            assertThat(exchange.getRequestHeaders().getFirst("Accept-Encoding")).isEqualTo("gzip");

            writeStringResponse(exchange, HttpStatus.SC_OK, """
                    <html>
                      <body>
                        <a href="artifact-1.jar">artifact</a>
                        <a href="encoded%20name.txt">encoded name</a>
                        <a href="nested/">nested directory</a>
                        <a href="../">parent</a>
                        <a href="?C=N;O=D">sort header</a>
                        <a href="mailto:dev@example.invalid">mail</a>
                      </body>
                    </html>
                    """);
        })) {
            HttpWagon wagon = connect(server);
            try {
                List files = wagon.getFileList("releases");

                assertThat(files).containsExactlyInAnyOrder("artifact-1.jar", "encoded name.txt", "nested/");
                server.assertNoFailure();
            } finally {
                wagon.disconnect();
            }
        }
    }

    @Test
    void getDownloadsGzipResponsesAndHandlesNotModified() throws Exception {
        byte[] plainBody = "downloaded through gzip".getBytes(UTF_8);
        byte[] gzippedBody = gzip(plainBody);
        String lastModified = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(Instant.parse("2024-01-02T03:04:05Z").atZone(ZoneOffset.UTC));
        AtomicInteger requests = new AtomicInteger();

        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            int requestNumber = requests.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/repository/content.txt");

            if (requestNumber == 1) {
                assertThat(exchange.getRequestHeaders().getFirst("Accept-Encoding")).isEqualTo("gzip");
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
                exchange.getResponseHeaders().add("Last-Modified", lastModified);
                writeBytesResponse(exchange, HttpStatus.SC_OK, gzippedBody);
            } else {
                assertThat(exchange.getRequestHeaders().getFirst("If-Modified-Since")).isNotBlank();
                exchange.sendResponseHeaders(HttpStatus.SC_NOT_MODIFIED, -1);
            }
        })) {
            HttpWagon wagon = connect(server);
            Path destination = tempDir.resolve("content.txt");
            try {
                wagon.get("content.txt", destination.toFile());
                assertThat(Files.readString(destination, UTF_8)).isEqualTo("downloaded through gzip");

                Files.writeString(destination, "unchanged", UTF_8);
                boolean downloaded = wagon.getIfNewer("content.txt", destination.toFile(), System.currentTimeMillis());

                assertThat(downloaded).isFalse();
                assertThat(Files.readString(destination, UTF_8)).isEqualTo("unchanged");
                assertThat(requests).hasValue(2);
                server.assertNoFailure();
            } finally {
                wagon.disconnect();
            }
        }
    }

    @Test
    void putUploadsFilesAndStreams() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            int requestNumber = requests.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("PUT");

            if (requestNumber == 1) {
                assertThat(exchange.getRequestURI().getRawPath()).isEqualTo("/repository/uploads/hello+world.txt");
                assertThat(new String(exchange.getRequestBody().readAllBytes(), UTF_8)).isEqualTo("file payload");
                writeStringResponse(exchange, HttpStatus.SC_CREATED, "created");
            } else {
                assertThat(exchange.getRequestURI().getRawPath()).isEqualTo("/repository/streams/data.bin");
                assertThat(new String(exchange.getRequestBody().readAllBytes(), UTF_8)).isEqualTo("stream payload");
                exchange.sendResponseHeaders(HttpStatus.SC_NO_CONTENT, -1);
            }
        })) {
            HttpWagon wagon = connect(server);
            Path source = tempDir.resolve("source.txt");
            Files.writeString(source, "file payload", UTF_8);
            byte[] streamPayload = "stream payload".getBytes(UTF_8);

            try {
                wagon.put(source.toFile(), "uploads/hello world.txt");
                wagon.putFromStream(new ByteArrayInputStream(streamPayload), "streams/data.bin", streamPayload.length,
                        source.toFile().lastModified());

                assertThat(requests).hasValue(2);
                server.assertNoFailure();
            } finally {
                wagon.disconnect();
            }
        }
    }

    @Test
    void getRespondsToBasicAuthenticationChallengeWithRepositoryCredentials() throws Exception {
        String expectedAuthorization = "Basic "
                + Base64.getEncoder().encodeToString("alice:s3cret".getBytes(UTF_8));
        AtomicInteger requests = new AtomicInteger();

        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            int requestNumber = requests.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/repository/private/data.txt");

            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (expectedAuthorization.equals(authorization)) {
                writeStringResponse(exchange, HttpStatus.SC_OK, "authenticated content");
                return;
            }

            assertThat(authorization).isNull();
            assertThat(requestNumber).isEqualTo(1);
            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"wagon-test\"");
            writeStringResponse(exchange, HttpStatus.SC_UNAUTHORIZED, "credentials required");
        })) {
            HttpWagon wagon = new HttpWagon();
            wagon.setTimeout(SHORT_TIMEOUT_MILLIS);
            AuthenticationInfo authenticationInfo = new AuthenticationInfo();
            authenticationInfo.setUserName("alice");
            authenticationInfo.setPassword("s3cret");
            wagon.connect(new Repository("test", server.uri("/repository").toString()), authenticationInfo);
            Path destination = tempDir.resolve("authenticated.txt");
            try {
                wagon.get("private/data.txt", destination.toFile());

                assertThat(Files.readString(destination, UTF_8)).isEqualTo("authenticated content");
                assertThat(requests).hasValue(2);
                server.assertNoFailure();
            } finally {
                wagon.disconnect();
            }
        }
    }

    @Test
    void getUsesConfiguredHttpProxyAndRespondsToProxyAuthenticationChallenge() throws Exception {
        String expectedProxyAuthorization = "Basic "
                + Base64.getEncoder().encodeToString("proxy-user:proxy-pass".getBytes(UTF_8));
        AtomicInteger requests = new AtomicInteger();

        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            int requestNumber = requests.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI()).isEqualTo(URI.create(
                    "http://repository.example.invalid/repository/proxied.txt"));
            assertThat(exchange.getRequestHeaders().getFirst("Host")).isEqualTo("repository.example.invalid");

            String proxyAuthorization = exchange.getRequestHeaders().getFirst("Proxy-Authorization");
            if (expectedProxyAuthorization.equals(proxyAuthorization)) {
                writeStringResponse(exchange, HttpStatus.SC_OK, "proxied content");
                return;
            }

            assertThat(proxyAuthorization).isNull();
            assertThat(requestNumber).isEqualTo(1);
            exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"wagon-proxy-test\"");
            writeStringResponse(exchange, HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, "proxy credentials required");
        })) {
            HttpWagon wagon = new HttpWagon();
            wagon.setTimeout(SHORT_TIMEOUT_MILLIS);
            ProxyInfo proxyInfo = new ProxyInfo();
            URI proxyUri = server.uri("/");
            proxyInfo.setType(ProxyInfo.PROXY_HTTP);
            proxyInfo.setHost(proxyUri.getHost());
            proxyInfo.setPort(proxyUri.getPort());
            proxyInfo.setUserName("proxy-user");
            proxyInfo.setPassword("proxy-pass");
            wagon.connect(new Repository("test", "http://repository.example.invalid/repository"), proxyInfo);
            Path destination = tempDir.resolve("proxied.txt");
            try {
                wagon.get("proxied.txt", destination.toFile());

                assertThat(Files.readString(destination, UTF_8)).isEqualTo("proxied content");
                assertThat(requests).hasValue(2);
                server.assertNoFailure();
            } finally {
                wagon.disconnect();
            }
        }
    }

    @Test
    void resourceExistsUsesHeadAndMapsHttpStatuses() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("HEAD");
            assertThat(exchange.getRequestHeaders().getFirst("X-Wagon-Test")).isEqualTo("configured");
            assertThat(exchange.getRequestHeaders().getFirst("Cache-control")).isNull();
            String path = exchange.getRequestURI().getPath();
            if ("/repository/present.dat".equals(path)) {
                exchange.sendResponseHeaders(HttpStatus.SC_OK, -1);
            } else if ("/repository/missing.dat".equals(path)) {
                exchange.sendResponseHeaders(HttpStatus.SC_NOT_FOUND, -1);
            } else if ("/repository/forbidden.dat".equals(path)) {
                exchange.sendResponseHeaders(HttpStatus.SC_FORBIDDEN, -1);
            } else if ("/repository/error.dat".equals(path)) {
                exchange.sendResponseHeaders(HttpStatus.SC_INTERNAL_SERVER_ERROR, -1);
            } else {
                writeStringResponse(exchange, HttpStatus.SC_BAD_REQUEST, "unexpected path: " + path);
            }
        })) {
            HttpWagon wagon = connect(server);
            wagon.setHttpConfiguration(new HttpConfiguration().setAll(new HttpMethodConfiguration()
                    .setUseDefaultHeaders(false)
                    .setConnectionTimeout(SHORT_TIMEOUT_MILLIS)
                    .addHeader("X-Wagon-Test", "configured")));
            try {
                assertThat(wagon.resourceExists("present.dat")).isTrue();
                assertThat(wagon.resourceExists("missing.dat")).isFalse();
                assertThatThrownBy(() -> wagon.resourceExists("forbidden.dat"))
                        .isInstanceOf(AuthorizationException.class)
                        .hasMessageContaining("Access denied");
                assertThatThrownBy(() -> wagon.resourceExists("error.dat"))
                        .isInstanceOf(TransferFailedException.class)
                        .hasMessageContaining("Return code is: 500");
                server.assertNoFailure();
            } finally {
                wagon.disconnect();
            }
        }
    }

    private static HttpWagon connect(TestHttpServer server) throws Exception {
        HttpWagon wagon = new HttpWagon();
        wagon.setTimeout(SHORT_TIMEOUT_MILLIS);
        wagon.connect(new Repository("test", server.uri("/repository").toString()));
        return wagon;
    }

    private static byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(content);
        }
        return output.toByteArray();
    }

    private static void writeStringResponse(HttpExchange exchange, int status, String body) throws IOException {
        writeBytesResponse(exchange, status, body.getBytes(UTF_8));
    }

    private static void writeBytesResponse(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @FunctionalInterface
    private interface ThrowingHttpHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private volatile Throwable failure;

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestHttpServer create(ThrowingHttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "wagon-http-test-server");
                thread.setDaemon(true);
                return thread;
            });
            TestHttpServer testServer = new TestHttpServer(server, executor);
            server.createContext("/", testServer.wrap(handler));
            server.setExecutor(executor);
            server.start();
            return testServer;
        }

        URI uri(String path) {
            return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()
                    + path);
        }

        void assertNoFailure() {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure != null) {
                throw new AssertionError("HTTP server handler failed", failure);
            }
        }

        private HttpHandler wrap(ThrowingHttpHandler handler) {
            return exchange -> {
                try {
                    handler.handle(exchange);
                } catch (Throwable throwable) {
                    failure = throwable;
                    if (exchange.getResponseCode() == -1) {
                        writeStringResponse(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "handler failure");
                    }
                } finally {
                    exchange.close();
                }
            };
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
