/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_http_lightweight;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Wagon_http_lightweightTest {
    private static final Object SYSTEM_PROPERTY_LOCK = new Object();
    private static final Instant LAST_MODIFIED = Instant.parse("2024-01-02T03:04:05Z");
    private static final String PLAIN_TEXT = "plain artifact downloaded through wagon";
    private static final String GZIP_TEXT = "compressed artifact transparently inflated by wagon";
    private static final String AUTHENTICATED_TEXT = "artifact downloaded after basic authentication";
    private static final String PROXIED_TEXT = "artifact downloaded through an authenticated HTTP proxy";
    private static final String AUTH_USERNAME = "wagon-user";
    private static final String AUTH_PASSWORD = "wagon-secret";
    private static final String PROXY_USERNAME = "proxy-user";
    private static final String PROXY_PASSWORD = "proxy-secret";

    @TempDir
    Path tempDirectory;

    @Test
    void downloadsPlainAndGzipResourcesAndHonorsFreshnessChecks() throws Exception {
        synchronized (SYSTEM_PROPERTY_LOCK) {
            withProxyPropertiesCleared(() -> {
                try (RepositoryServer server = RepositoryServer.start()) {
                    LightweightHttpWagon wagon = connectedWagon(server.repositoryUrl());
                    try {
                        Path plainTarget = tempDirectory.resolve("nested/plain.txt");
                        wagon.get("plain.txt", plainTarget.toFile());

                        assertThat(Files.readString(plainTarget)).isEqualTo(PLAIN_TEXT);
                        assertThat(server.firstHeadersFor("GET /repository/plain.txt"))
                                .containsEntry("accept-encoding", List.of("gzip"))
                                .containsEntry("pragma", List.of("no-cache"));

                        Path gzipTarget = tempDirectory.resolve("compressed.txt");
                        wagon.get("compressed.txt", gzipTarget.toFile());
                        assertThat(Files.readString(gzipTarget)).isEqualTo(GZIP_TEXT);

                        Files.writeString(plainTarget, "keep me", StandardCharsets.UTF_8);
                        boolean skipped = wagon.getIfNewer(
                                "plain.txt", plainTarget.toFile(), LAST_MODIFIED.plusSeconds(60).toEpochMilli());
                        assertThat(skipped).isFalse();
                        assertThat(Files.readString(plainTarget)).isEqualTo("keep me");

                        boolean downloaded = wagon.getIfNewer(
                                "plain.txt", plainTarget.toFile(), LAST_MODIFIED.minusSeconds(60).toEpochMilli());
                        assertThat(downloaded).isTrue();
                        assertThat(Files.readString(plainTarget)).isEqualTo(PLAIN_TEXT);
                    } finally {
                        wagon.disconnect();
                    }
                }
            });
        }
    }

    @Test
    void listsDirectoryEntriesAndMapsHeadStatusesToWagonResults() throws Exception {
        synchronized (SYSTEM_PROPERTY_LOCK) {
            withProxyPropertiesCleared(() -> {
                try (RepositoryServer server = RepositoryServer.start()) {
                    LightweightHttpWagon wagon = connectedWagon(server.repositoryUrl());
                    try {
                        assertThat(wagon.resourceExists("existing.txt")).isTrue();
                        assertThat(server.firstHeadersFor("HEAD /repository/existing.txt")).isNotEmpty();
                        assertThat(wagon.resourceExists("missing.txt")).isFalse();
                        assertThatExceptionOfType(AuthorizationException.class)
                                .isThrownBy(() -> wagon.resourceExists("forbidden.txt"))
                                .withMessageContaining("Access denied");

                        assertThat(wagon.getFileList("dir"))
                                .containsExactlyInAnyOrder("alpha.txt", "beta.txt", "encoded name.txt");
                    } finally {
                        wagon.disconnect();
                    }
                }
            });
        }
    }

    @Test
    void uploadsFilesWithPutAndReportsMissingDownloads() throws Exception {
        synchronized (SYSTEM_PROPERTY_LOCK) {
            withProxyPropertiesCleared(() -> {
                try (RepositoryServer server = RepositoryServer.start()) {
                    LightweightHttpWagon wagon = connectedWagon(server.repositoryUrl());
                    try {
                        Path source = tempDirectory.resolve("upload.txt");
                        Files.writeString(source, "content uploaded through PUT", StandardCharsets.UTF_8);

                        wagon.put(source.toFile(), "uploads/upload.txt");

                        assertThat(server.uploads()).containsEntry(
                                "/repository/uploads/upload.txt", "content uploaded through PUT");
                        assertThat(server.firstHeadersFor("PUT /repository/uploads/upload.txt")).isNotEmpty();

                        Path missingTarget = tempDirectory.resolve("missing.txt");
                        assertThatExceptionOfType(ResourceDoesNotExistException.class)
                                .isThrownBy(() -> wagon.get("missing.txt", missingTarget.toFile()))
                                .withMessageContaining("Unable to locate resource in repository");
                    } finally {
                        wagon.disconnect();
                    }
                }
            });
        }
    }

    @Test
    void downloadsResourcesProtectedByBasicAuthentication() throws Exception {
        synchronized (SYSTEM_PROPERTY_LOCK) {
            withProxyPropertiesCleared(() -> {
                try (RepositoryServer server = RepositoryServer.start()) {
                    AuthenticationInfo authenticationInfo = new AuthenticationInfo();
                    authenticationInfo.setUserName(AUTH_USERNAME);
                    authenticationInfo.setPassword(AUTH_PASSWORD);
                    Authenticator originalAuthenticator = Authenticator.getDefault();

                    LightweightHttpWagon wagon = connectedWagon(server.repositoryUrl(), authenticationInfo);
                    try {
                        Path target = tempDirectory.resolve("protected.txt");

                        wagon.get("protected.txt", target.toFile());

                        assertThat(Files.readString(target)).isEqualTo(AUTHENTICATED_TEXT);
                        assertThat(server.headersFor("GET /repository/protected.txt"))
                                .anySatisfy(headers -> assertThat(headers)
                                        .containsEntry("authorization", List.of(expectedAuthorizationHeader())));
                    } finally {
                        try {
                            wagon.disconnect();
                        } finally {
                            Authenticator.setDefault(originalAuthenticator);
                        }
                    }
                }
            });
        }
    }

    @Test
    void downloadsResourcesThroughHttpProxyWithBasicAuthentication() throws Exception {
        synchronized (SYSTEM_PROPERTY_LOCK) {
            withProxyPropertiesCleared(() -> {
                try (RepositoryServer server = RepositoryServer.start()) {
                    ProxyInfo proxyInfo = new ProxyInfo();
                    proxyInfo.setType("http");
                    proxyInfo.setHost("127.0.0.1");
                    proxyInfo.setPort(server.port());
                    proxyInfo.setUserName(PROXY_USERNAME);
                    proxyInfo.setPassword(PROXY_PASSWORD);
                    Authenticator originalAuthenticator = Authenticator.getDefault();

                    LightweightHttpWagon wagon = connectedWagon("http://upstream.example.test/repository", proxyInfo);
                    try {
                        Path target = tempDirectory.resolve("proxied.txt");

                        wagon.get("proxied.txt", target.toFile());

                        assertThat(Files.readString(target)).isEqualTo(PROXIED_TEXT);
                        assertThat(server.headersFor("GET /repository/proxied.txt"))
                                .anySatisfy(headers -> assertThat(headers).containsEntry(
                                        "proxy-authorization", List.of(expectedProxyAuthorizationHeader())));
                    } finally {
                        try {
                            wagon.disconnect();
                        } finally {
                            Authenticator.setDefault(originalAuthenticator);
                        }
                    }
                }
            });
        }
    }

    @Test
    void connectionAppliesAndRestoresProxySystemProperties() throws Exception {
        synchronized (SYSTEM_PROPERTY_LOCK) {
            Map<String, String> originalProperties = snapshotProxyProperties();
            try {
                System.setProperty("http.proxyHost", "original-http-host");
                System.setProperty("http.proxyPort", "18000");
                System.setProperty("https.proxyHost", "original-https-host");
                System.setProperty("https.proxyPort", "18443");
                System.setProperty("http.nonProxyHosts", "original.example|localhost");

                LightweightHttpWagon wagon = new LightweightHttpWagon();
                ProxyInfo proxyInfo = new ProxyInfo();
                proxyInfo.setType("http");
                proxyInfo.setHost("proxy.example.test");
                proxyInfo.setPort(3128);
                proxyInfo.setNonProxyHosts("127.0.0.1|localhost");

                wagon.connect(new Repository("test", "http://repo.example.test/base"), proxyInfo);
                assertThat(System.getProperty("http.proxyHost")).isEqualTo("proxy.example.test");
                assertThat(System.getProperty("http.proxyPort")).isEqualTo("3128");
                assertThat(System.getProperty("https.proxyHost")).isEqualTo("original-https-host");
                assertThat(System.getProperty("https.proxyPort")).isEqualTo("18443");
                assertThat(System.getProperty("http.nonProxyHosts")).isEqualTo("127.0.0.1|localhost");
                assertThat(wagon.getRepository().getUrl()).isEqualTo("http://repo.example.test/base");
                assertThat(wagon.getProxyInfo()).isSameAs(proxyInfo);

                wagon.disconnect();
                assertThat(System.getProperty("http.proxyHost")).isEqualTo("original-http-host");
                assertThat(System.getProperty("http.proxyPort")).isEqualTo("18000");
                assertThat(System.getProperty("https.proxyHost")).isEqualTo("original-https-host");
                assertThat(System.getProperty("https.proxyPort")).isEqualTo("18443");
                assertThat(System.getProperty("http.nonProxyHosts")).isEqualTo("original.example|localhost");
            } finally {
                restoreProxyProperties(originalProperties);
            }
        }
    }

    private static LightweightHttpWagon connectedWagon(String repositoryUrl) throws Exception {
        LightweightHttpWagon wagon = new LightweightHttpWagon();
        wagon.connect(new Repository("test", repositoryUrl));
        return wagon;
    }

    private static LightweightHttpWagon connectedWagon(String repositoryUrl, AuthenticationInfo authenticationInfo)
            throws Exception {
        LightweightHttpWagon wagon = new LightweightHttpWagon();
        wagon.connect(new Repository("test", repositoryUrl), authenticationInfo);
        return wagon;
    }

    private static LightweightHttpWagon connectedWagon(String repositoryUrl, ProxyInfo proxyInfo) throws Exception {
        LightweightHttpWagon wagon = new LightweightHttpWagon();
        wagon.connect(new Repository("test", repositoryUrl), proxyInfo);
        return wagon;
    }

    private static String expectedAuthorizationHeader() {
        return basicAuthorizationHeader(AUTH_USERNAME, AUTH_PASSWORD);
    }

    private static String expectedProxyAuthorizationHeader() {
        return basicAuthorizationHeader(PROXY_USERNAME, PROXY_PASSWORD);
    }

    private static String basicAuthorizationHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static void withProxyPropertiesCleared(ThrowingRunnable runnable) throws Exception {
        Map<String, String> originalProperties = snapshotProxyProperties();
        try {
            restoreProxyProperties(Collections.emptyMap());
            runnable.run();
        } finally {
            restoreProxyProperties(originalProperties);
        }
    }

    private static Map<String, String> snapshotProxyProperties() {
        Map<String, String> properties = new ConcurrentHashMap<>();
        putIfPresent(properties, "http.proxyHost");
        putIfPresent(properties, "http.proxyPort");
        putIfPresent(properties, "https.proxyHost");
        putIfPresent(properties, "https.proxyPort");
        putIfPresent(properties, "http.nonProxyHosts");
        return properties;
    }

    private static void putIfPresent(Map<String, String> properties, String name) {
        String value = System.getProperty(name);
        if (value != null) {
            properties.put(name, value);
        }
    }

    private static void restoreProxyProperties(Map<String, String> properties) {
        restoreProperty(properties, "http.proxyHost");
        restoreProperty(properties, "http.proxyPort");
        restoreProperty(properties, "https.proxyHost");
        restoreProperty(properties, "https.proxyPort");
        restoreProperty(properties, "http.nonProxyHosts");
    }

    private static void restoreProperty(Map<String, String> properties, String name) {
        String value = properties.get(name);
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class RepositoryServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final Map<String, List<Headers>> requests = new ConcurrentHashMap<>();
        private final Map<String, String> uploads = new ConcurrentHashMap<>();

        private RepositoryServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static RepositoryServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            RepositoryServer repositoryServer = new RepositoryServer(server, executor);
            server.createContext("/", repositoryServer::handle);
            server.setExecutor(executor);
            server.start();
            return repositoryServer;
        }

        String repositoryUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/repository";
        }

        int port() {
            return server.getAddress().getPort();
        }

        Map<String, String> uploads() {
            return uploads;
        }

        Headers firstHeadersFor(String requestKey) {
            return headersFor(requestKey).get(0);
        }

        List<Headers> headersFor(String requestKey) {
            List<Headers> matchingRequests = requests.getOrDefault(requestKey, List.of());
            assertThat(matchingRequests).as(requestKey).isNotEmpty();
            return matchingRequests;
        }

        @Override
        public void close() throws Exception {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            requests.computeIfAbsent(method + " " + path, ignored -> Collections.synchronizedList(new ArrayList<>()))
                    .add(normalizedHeaders(exchange.getRequestHeaders()));

            if ("GET".equals(method) && "/repository/plain.txt".equals(path)) {
                sendBytes(exchange, 200, PLAIN_TEXT.getBytes(StandardCharsets.UTF_8), "text/plain", false);
            } else if ("GET".equals(method) && "/repository/compressed.txt".equals(path)) {
                sendBytes(exchange, 200, gzip(GZIP_TEXT), "application/octet-stream", true);
            } else if ("GET".equals(method) && "/repository/dir/".equals(path)) {
                String html = """
                        <html><body>
                          <a href=\"alpha.txt\">alpha.txt</a>
                          <a href=\"beta.txt\">beta.txt</a>
                          <a href=\"encoded%20name.txt\">encoded name</a>
                          <a href=\"../\">Parent Directory</a>
                          <a href=\"nested/ignored/\">nested ignored</a>
                          <a href=\"?C=N;O=D\">sorting link ignored</a>
                        </body></html>
                        """;
                sendBytes(exchange, 200, html.getBytes(StandardCharsets.UTF_8), "text/html", false);
            } else if ("GET".equals(method) && "/repository/missing.txt".equals(path)) {
                sendBytes(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8), "text/plain", false);
            } else if ("GET".equals(method) && "/repository/protected.txt".equals(path)) {
                sendProtectedResource(exchange);
            } else if ("GET".equals(method) && "/repository/proxied.txt".equals(path)) {
                sendProxiedResource(exchange);
            } else if ("HEAD".equals(method) && "/repository/existing.txt".equals(path)) {
                sendHead(exchange, 200);
            } else if ("HEAD".equals(method) && "/repository/missing.txt".equals(path)) {
                sendHead(exchange, 404);
            } else if ("HEAD".equals(method) && "/repository/forbidden.txt".equals(path)) {
                sendHead(exchange, 403);
            } else if ("PUT".equals(method) && path.startsWith("/repository/uploads/")) {
                uploads.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                sendBytes(exchange, 201, new byte[0], "text/plain", false);
            } else {
                sendBytes(exchange, 500, ("unexpected " + method + " " + path).getBytes(StandardCharsets.UTF_8),
                        "text/plain", false);
            }
        }

        private static Headers normalizedHeaders(Headers headers) {
            Headers normalized = new Headers();
            headers.forEach((name, values) -> normalized.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
            return normalized;
        }

        private static void sendProtectedResource(HttpExchange exchange) throws IOException {
            if (expectedAuthorizationHeader().equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                sendBytes(exchange, 200, AUTHENTICATED_TEXT.getBytes(StandardCharsets.UTF_8), "text/plain", false);
                return;
            }

            exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"wagon-test\"");
            sendBytes(exchange, 401, "authentication required".getBytes(StandardCharsets.UTF_8), "text/plain", false);
        }

        private static void sendProxiedResource(HttpExchange exchange) throws IOException {
            String proxyAuthorization = exchange.getRequestHeaders().getFirst("Proxy-Authorization");
            if (expectedProxyAuthorizationHeader().equals(proxyAuthorization)) {
                sendBytes(exchange, 200, PROXIED_TEXT.getBytes(StandardCharsets.UTF_8), "text/plain", false);
                return;
            }

            byte[] body = "proxy authentication required".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Proxy-Authenticate", "Basic realm=\"wagon-proxy-test\"");
            sendBytes(exchange, 407, body, "text/plain", false);
        }

        private static void sendHead(HttpExchange exchange, int status) throws IOException {
            exchange.getResponseHeaders().set("Last-Modified", httpDate(LAST_MODIFIED));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }

        private static void sendBytes(
                HttpExchange exchange,
                int status,
                byte[] body,
                String contentType,
                boolean gzipEncoded) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Last-Modified", httpDate(LAST_MODIFIED));
            if (gzipEncoded) {
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        private static String httpDate(Instant instant) {
            return DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(instant);
        }
    }
}
