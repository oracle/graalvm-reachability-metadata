/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_transport_http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transport.http.ChecksumExtractor;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.transport.http.Nexus2ChecksumExtractor;
import org.eclipse.aether.transport.http.RFC9457.HttpRFC9457Exception;
import org.eclipse.aether.transport.http.RFC9457.RFC9457Parser;
import org.eclipse.aether.transport.http.RFC9457.RFC9457Payload;
import org.eclipse.aether.transport.http.RFC9457.RFC9457Reporter;
import org.eclipse.aether.transport.http.XChecksumChecksumExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_resolver_transport_httpTest {
    private static final String ARTIFACT_PATH = "/repository/com/example/demo/1.0/demo-1.0.jar";
    private static final String ARTIFACT_BODY = "resolved artifact contents";
    private static final String RESUMABLE_ARTIFACT_BODY = "already-downloaded remainder fetched over HTTP range";
    private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String MD5 = "0123456789abcdef0123456789abcdef";

    @Test
    void checksumExtractorsReadNexusAndStandardHttpHeaders() {
        BasicHttpResponse centralResponse = response(200, "OK");
        centralResponse.addHeader("x-checksum-sha1", SHA1);
        centralResponse.addHeader("x-checksum-md5", MD5);
        centralResponse.addHeader("x-goog-meta-checksum-sha1", "ignored-when-central-headers-exist");

        Map<String, String> centralChecksums = new XChecksumChecksumExtractor().extractChecksums(centralResponse);
        assertThat(centralChecksums).containsEntry("SHA-1", SHA1).containsEntry("MD5", MD5).hasSize(2);

        BasicHttpResponse googleStorageResponse = response(200, "OK");
        googleStorageResponse.addHeader("x-goog-meta-checksum-sha1", SHA1);
        googleStorageResponse.addHeader("x-goog-meta-checksum-md5", MD5);
        assertThat(new XChecksumChecksumExtractor().extractChecksums(googleStorageResponse))
                .containsEntry("SHA-1", SHA1)
                .containsEntry("MD5", MD5)
                .hasSize(2);

        BasicHttpResponse nexusResponse = response(200, "OK");
        nexusResponse.addHeader(HttpHeaders.ETAG, "\"{SHA1{" + SHA1 + "}}\"");
        assertThat(new Nexus2ChecksumExtractor().extractChecksums(nexusResponse))
                .containsExactly(Map.entry("SHA-1", SHA1));

        ChecksumExtractor extractor = new XChecksumChecksumExtractor();
        HttpGet request = new HttpGet("http://localhost/artifact.jar");
        extractor.prepareRequest(request);
        assertThat(request.getAllHeaders()).isEmpty();
        assertThat(extractor.retryWithoutExtractor(new HttpResponseException(400, "Bad Request"))).isFalse();
        assertThat(extractor.extractChecksums(response(200, "OK"))).isNull();
    }

    @Test
    void rfc9457ParserAndReporterExposeProblemDetails() throws Exception {
        String problem = """
                {
                  "type": "https://example.invalid/problems/quota",
                  "title": "Quota exceeded",
                  "status": 429,
                  "detail": "Too many artifact requests",
                  "instance": "urn:request:123"
                }
                """;

        RFC9457Payload parsed = RFC9457Parser.parse(problem);
        assertThat(parsed.getType()).isEqualTo(URI.create("https://example.invalid/problems/quota"));
        assertThat(parsed.getStatus()).isEqualTo(429);
        assertThat(parsed.getTitle()).isEqualTo("Quota exceeded");
        assertThat(parsed.getDetail()).isEqualTo("Too many artifact requests");
        assertThat(parsed.getInstance()).isEqualTo(URI.create("urn:request:123"));
        assertThat(parsed.toString()).contains("Quota exceeded", "Too many artifact requests");

        CloseableBasicHttpResponse response = closeableResponse(429, "Too Many Requests");
        response.addHeader(HttpHeaders.CONTENT_TYPE, "application/problem+json");
        response.setEntity(new StringEntity(problem, StandardCharsets.UTF_8));

        RFC9457Reporter reporter = RFC9457Reporter.INSTANCE;
        assertThat(reporter.isRFC9457Message(response)).isTrue();
        assertThatExceptionOfType(HttpRFC9457Exception.class)
                .isThrownBy(() -> reporter.generateException(response))
                .satisfies(exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(429);
                    assertThat(exception.getReasonPhrase()).isEqualTo("Too Many Requests (429)");
                    assertThat(exception.getPayload().getTitle()).isEqualTo("Quota exceeded");
                    assertThat(exception.getMessage()).contains("Quota exceeded");
                });

        CloseableBasicHttpResponse emptyProblemResponse = closeableResponse(503, "");
        emptyProblemResponse.addHeader(HttpHeaders.CONTENT_TYPE, "application/problem+json");
        emptyProblemResponse.setEntity(new StringEntity("", StandardCharsets.UTF_8));
        assertThatExceptionOfType(HttpRFC9457Exception.class)
                .isThrownBy(() -> reporter.generateException(emptyProblemResponse))
                .satisfies(exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(503);
                    assertThat(exception.getReasonPhrase()).isEmpty();
                    assertThat(exception.getPayload()).isSameAs(RFC9457Payload.INSTANCE);
                });

        CloseableBasicHttpResponse ordinaryJsonResponse = closeableResponse(500, "Server Error");
        ordinaryJsonResponse.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        assertThat(reporter.isRFC9457Message(ordinaryJsonResponse)).isFalse();
    }

    @Test
    void factoryValidatesRepositoryAndCreatesConfiguredHttpTransporter() throws Exception {
        HttpTransporterFactory factory = new HttpTransporterFactory(checksumExtractors());
        assertThat(factory.getPriority()).isEqualTo(5.0f);
        assertThat(factory.setPriority(9.5f)).isSameAs(factory);
        assertThat(factory.getPriority()).isEqualTo(9.5f);

        DefaultRepositorySystemSession session = session();
        RemoteRepository repository = new RemoteRepository.Builder("repo", "default", "http://127.0.0.1/repository/")
                .build();
        Transporter transporter = factory.newInstance(session, repository);
        try {
            assertThat(transporter.classify(new HttpResponseException(404, "Not Found")))
                    .isEqualTo(Transporter.ERROR_NOT_FOUND);
            assertThat(transporter.classify(new IOException("boom"))).isEqualTo(Transporter.ERROR_OTHER);
        } finally {
            transporter.close();
        }

        RemoteRepository fileRepository = new RemoteRepository.Builder("repo", "default", "file:/tmp/repository/")
                .build();
        assertThatExceptionOfType(NoTransporterException.class)
                .isThrownBy(() -> factory.newInstance(session, fileRepository));
        assertThatThrownBy(() -> factory.newInstance(null, repository)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> factory.newInstance(session, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void transporterAppliesConfiguredUserAgentAndRequestHeaders() throws Exception {
        AtomicReference<Map<String, String>> requestHeaders = new AtomicReference<>();
        try (HeaderCaptureServer server = HeaderCaptureServer.start(requestHeaders)) {
            DefaultRepositorySystemSession session = session();
            String userAgent = "resolver-http-functional-test";
            Map<String, String> configuredHeaders = new LinkedHashMap<>();
            configuredHeaders.put("X-Resolver-Test", "configured header");
            configuredHeaders.put("X-Resolver-Trace", "trace-token");
            session.setConfigProperty(ConfigurationProperties.USER_AGENT, userAgent);
            session.setConfigProperty(ConfigurationProperties.HTTP_HEADERS, configuredHeaders);

            HttpTransporterFactory factory = new HttpTransporterFactory(checksumExtractors());
            RemoteRepository repository = new RemoteRepository.Builder("local", "default", server.repositoryUrl())
                    .build();
            Transporter transporter = factory.newInstance(session, repository);
            try {
                transporter.peek(new PeekTask(URI.create("headers/artifact.txt")));

                assertThat(requestHeaders.get())
                        .containsEntry(HttpHeaders.USER_AGENT.toLowerCase(Locale.ROOT), userAgent)
                        .containsEntry("x-resolver-test", "configured header")
                        .containsEntry("x-resolver-trace", "trace-token");
            } finally {
                transporter.close();
            }
        }
    }

    @Test
    void transporterResumesFileDownloadUsingHttpRange(@TempDir Path tempDir) throws Exception {
        String localPrefix = RESUMABLE_ARTIFACT_BODY.substring(0, "already-downloaded".length());
        Path artifactFile = tempDir.resolve("artifact.txt");
        Files.writeString(artifactFile, localPrefix, StandardCharsets.UTF_8);

        try (ResumableDownloadServer server = ResumableDownloadServer.start()) {
            HttpTransporterFactory factory = new HttpTransporterFactory(checksumExtractors());
            RemoteRepository repository = new RemoteRepository.Builder("local", "default", server.repositoryUrl())
                    .build();
            Transporter transporter = factory.newInstance(session(), repository);
            try {
                GetTask getTask = new GetTask(URI.create("resumable/artifact.txt"))
                        .setDataFile(artifactFile.toFile(), true);
                transporter.get(getTask);

                assertThat(Files.readString(artifactFile, StandardCharsets.UTF_8))
                        .isEqualTo(RESUMABLE_ARTIFACT_BODY);
                assertThat(server.rangeHeader()).isEqualTo("bytes=" + localPrefix.length() + "-");
                assertThat(server.acceptEncodingHeader()).isEqualTo("identity");
                assertThat(server.ifUnmodifiedSinceHeader()).isNotBlank();
                assertThat(getTask.getChecksums()).containsEntry("SHA-1", SHA1).containsEntry("MD5", MD5);
            } finally {
                transporter.close();
            }
        }
    }

    @Test
    void transporterPerformsPeekGetPutAndReportsProblemJson() throws Exception {
        AtomicReference<String> uploadedBody = new AtomicReference<>();
        try (TestRepositoryServer server = TestRepositoryServer.start(uploadedBody)) {
            HttpTransporterFactory factory = new HttpTransporterFactory(checksumExtractors());
            RemoteRepository repository = new RemoteRepository.Builder("local", "default", server.repositoryUrl())
                    .build();
            Transporter transporter = factory.newInstance(session(), repository);
            try {
                transporter.peek(new PeekTask(URI.create("com/example/demo/1.0/demo-1.0.jar")));

                GetTask getTask = new GetTask(URI.create("com/example/demo/1.0/demo-1.0.jar"));
                transporter.get(getTask);
                assertThat(getTask.getDataString()).isEqualTo(ARTIFACT_BODY);
                assertThat(getTask.getChecksums()).containsEntry("SHA-1", SHA1).containsEntry("MD5", MD5);

                PutTask putTask = new PutTask(URI.create("uploads/new-artifact.txt"))
                        .setDataString("uploaded by resolver");
                transporter.put(putTask);
                assertThat(uploadedBody).hasValue("uploaded by resolver");

                assertThatExceptionOfType(HttpResponseException.class)
                        .isThrownBy(() -> transporter.peek(new PeekTask(URI.create("missing.txt"))))
                        .satisfies(exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(404);
                            assertThat(transporter.classify(exception)).isEqualTo(Transporter.ERROR_NOT_FOUND);
                        });

                assertThatExceptionOfType(HttpRFC9457Exception.class)
                        .isThrownBy(() -> transporter.get(new GetTask(URI.create("problem.json"))))
                        .satisfies(exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(422);
                            assertThat(exception.getPayload().getTitle()).isEqualTo("Invalid artifact");
                            assertThat(exception.getPayload().getDetail())
                                    .isEqualTo("The requested artifact is malformed");
                            assertThat(transporter.classify(exception)).isEqualTo(Transporter.ERROR_OTHER);
                        });
            } finally {
                transporter.close();
            }
        }
    }

    private static DefaultRepositorySystemSession session() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, 2_000);
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, 2_000);
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 0);
        session.setConfigProperty(ConfigurationProperties.HTTP_EXPECT_CONTINUE, "false");
        return session;
    }

    private static Map<String, ChecksumExtractor> checksumExtractors() {
        Map<String, ChecksumExtractor> extractors = new LinkedHashMap<>();
        extractors.put(XChecksumChecksumExtractor.NAME, new XChecksumChecksumExtractor());
        extractors.put(Nexus2ChecksumExtractor.NAME, new Nexus2ChecksumExtractor());
        return extractors;
    }

    private static BasicHttpResponse response(int statusCode, String reasonPhrase) {
        return new BasicHttpResponse(HttpVersion.HTTP_1_1, statusCode, reasonPhrase);
    }

    private static CloseableBasicHttpResponse closeableResponse(int statusCode, String reasonPhrase) {
        return new CloseableBasicHttpResponse(HttpVersion.HTTP_1_1, statusCode, reasonPhrase);
    }

    private static final class CloseableBasicHttpResponse extends BasicHttpResponse implements CloseableHttpResponse {
        private CloseableBasicHttpResponse(ProtocolVersion version, int statusCode, String reasonPhrase) {
            super(version, statusCode, reasonPhrase);
        }

        @Override
        public void close() {
            EntityUtils.consumeQuietly(getEntity());
        }
    }

    private static final class HeaderCaptureServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private HeaderCaptureServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static HeaderCaptureServer start(AtomicReference<Map<String, String>> requestHeaders) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "resolver-http-headers-test-server");
                thread.setDaemon(true);
                return thread;
            });
            HeaderCaptureServer repositoryServer = new HeaderCaptureServer(server, executor);
            server.createContext("/", exchange -> repositoryServer.handle(exchange, requestHeaders));
            server.setExecutor(executor);
            server.start();
            return repositoryServer;
        }

        String repositoryUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/repository/";
        }

        private void handle(HttpExchange exchange, AtomicReference<Map<String, String>> requestHeaders)
                throws IOException {
            try {
                if (!"/repository/headers/artifact.txt".equals(exchange.getRequestURI().getPath())
                        || !"HEAD".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                Map<String, String> capturedHeaders = new LinkedHashMap<>();
                exchange.getRequestHeaders().forEach((name, values) -> capturedHeaders.put(
                        name.toLowerCase(Locale.ROOT), String.join(",", values)));
                requestHeaders.set(capturedHeaders);
                exchange.sendResponseHeaders(200, -1);
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(1).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private static final class ResumableDownloadServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicReference<String> rangeHeader = new AtomicReference<>();
        private final AtomicReference<String> acceptEncodingHeader = new AtomicReference<>();
        private final AtomicReference<String> ifUnmodifiedSinceHeader = new AtomicReference<>();

        private ResumableDownloadServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static ResumableDownloadServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "resolver-http-resume-test-server");
                thread.setDaemon(true);
                return thread;
            });
            ResumableDownloadServer repositoryServer = new ResumableDownloadServer(server, executor);
            server.createContext("/", repositoryServer::handle);
            server.setExecutor(executor);
            server.start();
            return repositoryServer;
        }

        String repositoryUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/repository/";
        }

        String rangeHeader() {
            return rangeHeader.get();
        }

        String acceptEncodingHeader() {
            return acceptEncodingHeader.get();
        }

        String ifUnmodifiedSinceHeader() {
            return ifUnmodifiedSinceHeader.get();
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"/repository/resumable/artifact.txt".equals(exchange.getRequestURI().getPath())
                        || !"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                rangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
                acceptEncodingHeader.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
                ifUnmodifiedSinceHeader.set(exchange.getRequestHeaders().getFirst("If-Unmodified-Since"));

                long offset = parseRangeOffset(rangeHeader.get());
                byte[] fullBody = RESUMABLE_ARTIFACT_BODY.getBytes(StandardCharsets.UTF_8);
                if (offset <= 0 || offset >= fullBody.length) {
                    exchange.sendResponseHeaders(416, -1);
                    return;
                }

                byte[] remainingBody = RESUMABLE_ARTIFACT_BODY.substring((int) offset)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Range",
                        "bytes " + offset + "-" + (fullBody.length - 1) + "/" + fullBody.length);
                exchange.getResponseHeaders().add("x-checksum-sha1", SHA1);
                exchange.getResponseHeaders().add("x-checksum-md5", MD5);
                exchange.sendResponseHeaders(206, remainingBody.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(remainingBody);
                }
            } finally {
                exchange.close();
            }
        }

        private long parseRangeOffset(String range) {
            if (range == null || !range.startsWith("bytes=") || !range.endsWith("-")) {
                return -1;
            }
            return Long.parseLong(range.substring("bytes=".length(), range.length() - 1));
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(1).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private static final class TestRepositoryServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestRepositoryServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestRepositoryServer start(AtomicReference<String> uploadedBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "resolver-http-test-server");
                thread.setDaemon(true);
                return thread;
            });
            TestRepositoryServer repositoryServer = new TestRepositoryServer(server, executor);
            server.createContext("/", exchange -> repositoryServer.handle(exchange, uploadedBody));
            server.setExecutor(executor);
            server.start();
            return repositoryServer;
        }

        String repositoryUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/repository/";
        }

        private void handle(HttpExchange exchange, AtomicReference<String> uploadedBody) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if (ARTIFACT_PATH.equals(path)) {
                    handleArtifact(exchange);
                } else if ("/repository/uploads/new-artifact.txt".equals(path)
                        && "PUT".equals(exchange.getRequestMethod())) {
                    uploadedBody.set(readRequestBody(exchange));
                    exchange.sendResponseHeaders(201, -1);
                } else if ("/repository/problem.json".equals(path)) {
                    byte[] body = """
                            {
                              "type": "https://example.invalid/problems/invalid-artifact",
                              "title": "Invalid artifact",
                              "status": 422,
                              "detail": "The requested artifact is malformed"
                            }
                            """.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/problem+json");
                    exchange.sendResponseHeaders(422, body.length);
                    try (OutputStream outputStream = exchange.getResponseBody()) {
                        outputStream.write(body);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } finally {
                exchange.close();
            }
        }

        private void handleArtifact(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("x-checksum-sha1", SHA1);
            exchange.getResponseHeaders().add("x-checksum-md5", MD5);
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] body = ARTIFACT_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(body);
                }
                return;
            }
            exchange.sendResponseHeaders(405, -1);
        }

        private String readRequestBody(HttpExchange exchange) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            exchange.getRequestBody().transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(1).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        }
    }
}
