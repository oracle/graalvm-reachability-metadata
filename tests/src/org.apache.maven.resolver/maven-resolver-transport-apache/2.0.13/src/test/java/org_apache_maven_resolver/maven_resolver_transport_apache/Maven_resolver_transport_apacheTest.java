/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_transport_apache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractor;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys;
import org.eclipse.aether.transport.apache.ApacheTransporterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_resolver_transport_apacheTest {
    private static final String FIXED_LAST_MODIFIED = DateTimeFormatter.RFC_1123_DATE_TIME
            .format(Instant.parse("2024-01-02T03:04:05Z").atOffset(ZoneOffset.UTC));
    private static final ChecksumExtractor RESPONSE_CHECKSUMS = headers -> {
        String sha1 = headers.apply("X-Checksum-Sha1");
        if (sha1 == null) {
            return Map.of();
        }
        return Map.of("SHA-1", sha1);
    };

    @TempDir
    private Path tempDirectory;

    @Test
    void factoryExposesPriorityAndRejectsUnsupportedRepositories() {
        ApacheTransporterFactory factory = newFactory();

        assertThat(ApacheTransporterFactory.NAME).isEqualTo("apache");
        assertThat(factory.getPriority()).isEqualTo(5.0f);
        assertThat(factory.setPriority(7.25f)).isSameAs(factory);
        assertThat(factory.getPriority()).isEqualTo(7.25f);
        assertThatNullPointerException().isThrownBy(() -> new ApacheTransporterFactory(null, new SimplePathProcessor()))
                .withMessageContaining("checksumExtractor");
        assertThatNullPointerException().isThrownBy(() -> new ApacheTransporterFactory(RESPONSE_CHECKSUMS, null))
                .withMessageContaining("pathProcessor");

        RemoteRepository fileRepository = new RemoteRepository.Builder("local", "default", "file:/tmp/repository")
                .build();
        assertThatExceptionOfType(NoTransporterException.class)
                .isThrownBy(() -> factory.newInstance(newSession(), fileRepository))
                .satisfies(error -> assertThat(error.getRepository()).isSameAs(fileRepository));
    }

    @Test
    void getPeekAndPutExchangeDataHeadersAndChecksums() throws Exception {
        byte[] payload = "resolver transport payload".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> downloadedHeader = new AtomicReference<>();
        AtomicReference<String> uploadedBody = new AtomicReference<>();
        AtomicReference<String> uploadedHeader = new AtomicReference<>();
        AtomicReference<String> uploadedPath = new AtomicReference<>();
        List<String> methods = new ArrayList<>();

        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            methods.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            if ("/repo/artifact.txt".equals(exchange.getRequestURI().getPath())) {
                downloadedHeader.set(exchange.getRequestHeaders().getFirst("X-Resolver-Test"));
                Headers headers = exchange.getResponseHeaders();
                headers.add("Last-Modified", FIXED_LAST_MODIFIED);
                headers.add("X-Checksum-Sha1", "6b61b6f78d4a6ebf01e4ee71dbba9b4d8a01f3c4");
                if ("HEAD".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(200, -1);
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(200, payload.length);
                    exchange.getResponseBody().write(payload);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
                return;
            }
            if ("/repo/uploads/metadata.xml".equals(exchange.getRequestURI().getPath())
                    && "PUT".equals(exchange.getRequestMethod())) {
                uploadedPath.set(exchange.getRequestURI().getPath());
                uploadedHeader.set(exchange.getRequestHeaders().getFirst("X-Resolver-Test"));
                uploadedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(201, -1);
                return;
            }
            exchange.sendResponseHeaders(404, -1);
        })) {
            try (HttpTransporter transporter = newTransporter(server)) {
                PeekTask peekTask = new PeekTask(URI.create("artifact.txt"));
                transporter.peek(peekTask);

                GetTask getTask = new GetTask(URI.create("artifact.txt"));
                transporter.get(getTask);

                Path uploadSource = tempDirectory.resolve("metadata.xml");
                Files.writeString(uploadSource, "<metadata>uploaded</metadata>", StandardCharsets.UTF_8);
                transporter.put(new PutTask(URI.create("uploads/metadata.xml")).setDataPath(uploadSource));

                assertThat(getTask.getDataString()).isEqualTo("resolver transport payload");
                assertThat(getTask.getChecksums()).containsEntry("SHA-1", "6b61b6f78d4a6ebf01e4ee71dbba9b4d8a01f3c4");
                assertThat(downloadedHeader.get()).isEqualTo("enabled");
                assertThat(uploadedBody.get()).isEqualTo("<metadata>uploaded</metadata>");
                assertThat(uploadedHeader.get()).isEqualTo("enabled");
                assertThat(uploadedPath.get()).isEqualTo("/repo/uploads/metadata.xml");
                assertThat(methods).containsExactly(
                        "HEAD /repo/artifact.txt", "GET /repo/artifact.txt", "PUT /repo/uploads/metadata.xml");
            }
        }
    }

    @Test
    void getCanResumeIntoAFileAndApplyServerTimestamp() throws Exception {
        AtomicReference<String> rangeHeader = new AtomicReference<>();
        AtomicReference<String> ifUnmodifiedSinceHeader = new AtomicReference<>();

        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            if ("/repo/resumable.bin".equals(exchange.getRequestURI().getPath())
                    && "GET".equals(exchange.getRequestMethod())) {
                rangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
                ifUnmodifiedSinceHeader.set(exchange.getRequestHeaders().getFirst("If-Unmodified-Since"));
                byte[] suffix = "def".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Range", "bytes 3-5/6");
                exchange.getResponseHeaders().add("Last-Modified", FIXED_LAST_MODIFIED);
                exchange.sendResponseHeaders(206, suffix.length);
                exchange.getResponseBody().write(suffix);
                return;
            }
            exchange.sendResponseHeaders(404, -1);
        })) {
            Path target = tempDirectory.resolve("resumable.bin");
            Files.writeString(target, "abc", StandardCharsets.UTF_8);
            Files.setLastModifiedTime(target, FileTime.from(Instant.parse("2024-01-02T03:04:05Z")));

            try (HttpTransporter transporter = newTransporter(server)) {
                GetTask getTask = new GetTask(URI.create("resumable.bin")).setDataPath(target, true);
                transporter.get(getTask);

                assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo("abcdef");
                assertThat(rangeHeader.get()).isEqualTo("bytes=3-");
                assertThat(ifUnmodifiedSinceHeader.get()).isNotBlank();
                assertThat(Files.getLastModifiedTime(target).toMillis())
                        .isEqualTo(Instant.parse("2024-01-02T03:04:05Z").toEpochMilli());
            }
        }
    }

    @Test
    void getFollowsConfiguredRedirectsToDownloadRelocatedResource() throws Exception {
        byte[] payload = "relocated resolver payload".getBytes(StandardCharsets.UTF_8);
        List<String> methods = new ArrayList<>();

        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            methods.add(method + " " + path);
            if ("GET".equals(method) && "/repo/redirected.txt".equals(path)) {
                exchange.getResponseHeaders().add("Location", "/repo/relocated/artifact.txt");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            if ("GET".equals(method) && "/repo/relocated/artifact.txt".equals(path)) {
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
                return;
            }
            exchange.sendResponseHeaders(404, -1);
        })) {
            RepositorySystemSession session = newSession()
                    .setConfigProperty(ApacheTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_REDIRECTS, true)
                    .setConfigProperty(ApacheTransporterConfigurationKeys.CONFIG_PROP_MAX_REDIRECTS, 2);

            try (HttpTransporter transporter = newTransporter(server, session)) {
                GetTask getTask = new GetTask(URI.create("redirected.txt"));
                transporter.get(getTask);

                assertThat(getTask.getDataString()).isEqualTo("relocated resolver payload");
                assertThat(methods).containsExactly(
                        "GET /repo/redirected.txt", "GET /repo/relocated/artifact.txt");
            }
        }
    }

    @Test
    void classifyDistinguishesMissingHttpResourcesFromOtherErrors() throws Exception {
        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            if ("/repo/missing.txt".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.sendResponseHeaders(500, -1);
        })) {
            try (HttpTransporter transporter = newTransporter(server)) {
                assertThatThrownBy(() -> transporter.peek(new PeekTask(URI.create("missing.txt"))))
                        .satisfies(error -> assertThat(transporter.classify(error))
                                .isEqualTo(Transporter.ERROR_NOT_FOUND));
                assertThatThrownBy(() -> transporter.peek(new PeekTask(URI.create("server-error.txt"))))
                        .satisfies(error -> assertThat(transporter.classify(error)).isEqualTo(Transporter.ERROR_OTHER));
            }
        }
    }

    @Test
    void putCreatesWebDavDirectoriesBeforeUploadingNestedArtifact() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> createdDirectories = new ArrayList<>();
        AtomicReference<String> uploadedBody = new AtomicReference<>();

        try (TestHttpServer server = TestHttpServer.create(exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            methods.add(method + " " + path);
            if ("OPTIONS".equals(method) && "/repo/releases/snapshots/metadata.xml".equals(path)) {
                exchange.getResponseHeaders().add("Dav", "1,2");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if ("MKCOL".equals(method) && "/repo/releases/snapshots/".equals(path)) {
                if (createdDirectories.contains("/repo/releases/")) {
                    createdDirectories.add(path);
                    exchange.sendResponseHeaders(201, -1);
                } else {
                    exchange.sendResponseHeaders(409, -1);
                }
                return;
            }
            if ("MKCOL".equals(method) && "/repo/releases/".equals(path)) {
                createdDirectories.add(path);
                exchange.sendResponseHeaders(201, -1);
                return;
            }
            if ("PUT".equals(method) && "/repo/releases/snapshots/metadata.xml".equals(path)) {
                uploadedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(201, -1);
                return;
            }
            exchange.sendResponseHeaders(404, -1);
        })) {
            Path uploadSource = tempDirectory.resolve("nested-metadata.xml");
            Files.writeString(uploadSource, "<metadata>webdav</metadata>", StandardCharsets.UTF_8);
            RepositorySystemSession session = newSession()
                    .setConfigProperty("aether.transport.http.supportWebDav", true);

            try (HttpTransporter transporter = newTransporter(server, session)) {
                transporter.put(new PutTask(URI.create("releases/snapshots/metadata.xml")).setDataPath(uploadSource));

                assertThat(uploadedBody.get()).isEqualTo("<metadata>webdav</metadata>");
                assertThat(createdDirectories).containsExactly("/repo/releases/", "/repo/releases/snapshots/");
                assertThat(methods).containsExactly(
                        "OPTIONS /repo/releases/snapshots/metadata.xml",
                        "MKCOL /repo/releases/snapshots/",
                        "MKCOL /repo/releases/",
                        "MKCOL /repo/releases/snapshots/",
                        "PUT /repo/releases/snapshots/metadata.xml");
            }
        }
    }

    private static ApacheTransporterFactory newFactory() {
        return new ApacheTransporterFactory(RESPONSE_CHECKSUMS, new SimplePathProcessor());
    }

    private static HttpTransporter newTransporter(TestHttpServer server) throws NoTransporterException {
        return newTransporter(server, newSession());
    }

    private static HttpTransporter newTransporter(TestHttpServer server, RepositorySystemSession session)
            throws NoTransporterException {
        RemoteRepository repository = new RemoteRepository.Builder(
                "test-repository", "default", server.repositoryUrl()).build();
        return newFactory().newInstance(session, repository);
    }

    private static DefaultRepositorySystemSession newSession() {
        return new DefaultRepositorySystemSession()
                .setConfigProperty("aether.transport.http.connectTimeout", 2_000)
                .setConfigProperty("aether.transport.http.requestTimeout", 2_000)
                .setConfigProperty("aether.transport.http.retryHandler.count", 0)
                .setConfigProperty("aether.transport.http.expectContinue", false)
                .setConfigProperty("aether.transport.http.headers", Map.of("X-Resolver-Test", "enabled"))
                .setConfigProperty(ApacheTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_REDIRECTS, false);
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestHttpServer create(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "resolver-apache-transport-test-server");
                thread.setDaemon(true);
                return thread;
            });
            server.createContext("/", exchange -> {
                try (exchange) {
                    handler.handle(exchange);
                }
            });
            server.setExecutor(executor);
            server.start();
            return new TestHttpServer(server, executor);
        }

        String repositoryUrl() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/repo/";
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class SimplePathProcessor implements PathProcessor {
        @Override
        public boolean setLastModified(Path path, long millis) throws IOException {
            Files.setLastModifiedTime(path, FileTime.fromMillis(millis));
            return true;
        }

        @Override
        public void write(Path target, String data) throws IOException {
            Files.writeString(target, data, StandardCharsets.UTF_8);
        }

        @Override
        public void write(Path target, InputStream source) throws IOException {
            try (source) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        @Override
        public void writeWithBackup(Path target, String data) throws IOException {
            write(target, data);
        }

        @Override
        public void writeWithBackup(Path target, InputStream source) throws IOException {
            write(target, source);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public long copy(Path source, Path target, ProgressListener listener) throws IOException {
            long bytes = 0L;
            try (InputStream input = Files.newInputStream(source);
                    OutputStream output = Files.newOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    bytes += read;
                    if (listener != null) {
                        listener.progressed(ByteBuffer.wrap(buffer, 0, read));
                    }
                }
            }
            return bytes;
        }

        @Override
        public TempFile newTempFile() throws IOException {
            return new SimpleTempFile(Files.createTempFile("resolver-apache", ".tmp"));
        }

        @Override
        public CollocatedTempFile newTempFile(Path target) throws IOException {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, target.getFileName() + ".", ".tmp");
            return new SimpleCollocatedTempFile(tempFile, target);
        }
    }

    private static class SimpleTempFile implements PathProcessor.TempFile {
        private final Path path;

        SimpleTempFile(Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }

    private static final class SimpleCollocatedTempFile extends SimpleTempFile
            implements PathProcessor.CollocatedTempFile {
        private final Path path;
        private final Path target;

        SimpleCollocatedTempFile(Path path, Path target) {
            super(path);
            this.path = path;
            this.target = target;
        }

        @Override
        public void move() throws IOException {
            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
