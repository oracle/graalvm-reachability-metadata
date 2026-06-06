/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.simpleclient_httpserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Predicate;
import io.prometheus.client.Supplier;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.exporter.SampleNameFilterSupplier;
import org.junit.jupiter.api.Test;

public class Simpleclient_httpserverTest {
    private static final int CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final int READ_TIMEOUT_MILLIS = 2_000;

    @Test
    void httpServerExportsMetricsAndHealthCheckOnLoopbackAddress() throws Exception {
        CollectorRegistry registry = new CollectorRegistry();
        Counter requests = Counter.build("server_requests_total", "Server requests.")
                .labelNames("route")
                .register(registry);
        requests.labels("/api").inc(3.0);
        Gauge inProgress = Gauge.build("server_in_progress", "In progress requests.")
                .register(registry);
        inProgress.set(2.0);

        try (HTTPServer server = new HTTPServer.Builder()
                .withInetAddress(loopbackAddress())
                .withPort(0)
                .withRegistry(registry)
                .withDaemonThreads(true)
                .build()) {
            assertThat(server.getPort()).isPositive();

            HttpResponse metrics = request(server, "GET", "/metrics", List.of(), false);
            assertThat(metrics.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(metrics.header("Content-Type")).startsWith("text/plain");
            assertThat(metrics.header("Content-Length")).isNotBlank();
            assertThat(metrics.body)
                    .contains("# HELP server_requests_total Server requests.")
                    .contains("# TYPE server_requests_total counter")
                    .contains("server_requests_total{route=\"/api\",} 3.0")
                    .contains("server_in_progress 2.0");

            HttpResponse root = request(server, "GET", "/", List.of(), false);
            assertThat(root.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(root.body).contains("server_requests_total{route=\"/api\",} 3.0");

            HttpResponse health = request(server, "GET", "/-/healthy", List.of(), false);
            assertThat(health.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(health.body).isEqualTo("Exporter is Healthy.");
        }
    }

    @Test
    void queryAndConfiguredSampleNameFiltersRestrictExportedSamples() throws Exception {
        CollectorRegistry registry = new CollectorRegistry();
        Counter exported = Counter.build("exported_jobs_total", "Exported jobs.")
                .register(registry);
        Counter hidden = Counter.build("hidden_jobs_total", "Hidden jobs.")
                .register(registry);
        exported.inc(5.0);
        hidden.inc(7.0);

        Predicate<String> predicate = sampleName -> sampleName.startsWith("exported_");
        Supplier<Predicate<String>> supplier = SampleNameFilterSupplier.of(predicate);
        assertThat(supplier.get()).isSameAs(predicate);

        try (HTTPServer server = new HTTPServer.Builder()
                .withInetAddress(loopbackAddress())
                .withPort(0)
                .withRegistry(registry)
                .withSampleNameFilterSupplier(supplier)
                .withDaemonThreads(true)
                .build()) {
            HttpResponse allAllowed = request(server, "GET", "/metrics", List.of(), false);
            assertThat(allAllowed.body)
                    .contains("exported_jobs_total 5.0")
                    .doesNotContain("hidden_jobs_total");

            HttpResponse queryAllowed = request(
                    server,
                    "GET",
                    "/metrics?name%5B%5D=exported_jobs_total&name%5B%5D=hidden_jobs_total",
                    List.of(),
                    false);
            assertThat(queryAllowed.body)
                    .contains("exported_jobs_total 5.0")
                    .doesNotContain("hidden_jobs_total");

            HttpResponse queryDisallowed = request(
                    server,
                    "GET",
                    "/metrics?name%5B%5D=hidden_jobs_total",
                    List.of(),
                    false);
            assertThat(queryDisallowed.body)
                    .doesNotContain("exported_jobs_total")
                    .doesNotContain("hidden_jobs_total");
        }
    }

    @Test
    void acceptHeaderSelectsOpenMetricsTextFormat() throws Exception {
        CollectorRegistry registry = new CollectorRegistry();
        Gauge openMetric = Gauge.build("open_metric", "OpenMetrics negotiated gauge.")
                .labelNames("state", "kind")
                .register(registry);
        openMetric.labels("ready", "primary").set(8.0);

        try (HTTPServer server = new HTTPServer.Builder()
                .withInetAddress(loopbackAddress())
                .withPort(0)
                .withRegistry(registry)
                .withDaemonThreads(true)
                .build()) {
            HttpResponse response = request(
                    server,
                    "GET",
                    "/metrics",
                    List.of(new Header("Accept", "text/plain;q=0.5, application/openmetrics-text; version=1.0.0")),
                    false);

            assertThat(response.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.header("Content-Type"))
                    .isEqualTo("application/openmetrics-text; version=1.0.0; charset=utf-8");
            assertThat(response.body)
                    .contains("# TYPE open_metric gauge")
                    .contains("# HELP open_metric OpenMetrics negotiated gauge.")
                    .contains("open_metric{state=\"ready\",kind=\"primary\"} 8.0")
                    .endsWith("# EOF\n");
        }
    }

    @Test
    void gzipCompressionAndHeadRequestsUseHttpSemantics() throws Exception {
        CollectorRegistry registry = new CollectorRegistry();
        Gauge temperature = Gauge.build("room_temperature_celsius", "Room temperature.")
                .register(registry);
        temperature.set(21.5);

        try (HTTPServer server = new HTTPServer.Builder()
                .withHostname("127.0.0.1")
                .withPort(0)
                .withRegistry(registry)
                .withDaemonThreads(true)
                .build()) {
            HttpResponse compressed = request(
                    server,
                    "GET",
                    "/metrics",
                    List.of(new Header("Accept-Encoding", "br, gzip")),
                    true);
            assertThat(compressed.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(compressed.header("Content-Encoding")).isEqualTo("gzip");
            assertThat(compressed.body).contains("room_temperature_celsius 21.5");

            HttpResponse head = request(server, "HEAD", "/metrics", List.of(), false);
            assertThat(head.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(head.header("Content-Length")).isNotBlank();
            assertThat(head.body).isEmpty();
        }
    }

    @Test
    void customHttpServerAndAuthenticatorProtectMetricsEndpoints() throws Exception {
        CollectorRegistry registry = new CollectorRegistry();
        Counter secureRequests = Counter.build("secure_requests_total", "Secure requests.")
                .register(registry);
        secureRequests.inc();

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(loopbackAddress(), 0), 3);
        try (HTTPServer server = new HTTPServer.Builder()
                .withHttpServer(httpServer)
                .withRegistry(registry)
                .withAuthenticator(new BasicAuthenticator("metrics") {
                    @Override
                    public boolean checkCredentials(String username, String password) {
                        return "prometheus".equals(username) && "secret".equals(password);
                    }
                })
                .withDaemonThreads(true)
                .build()) {
            HttpResponse unauthorized = request(server, "GET", "/metrics", List.of(), false);
            assertThat(unauthorized.statusCode).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
            assertThat(unauthorized.header("WWW-Authenticate")).contains("Basic");

            String token = Base64.getEncoder().encodeToString("prometheus:secret".getBytes(UTF_8));
            HttpResponse authorized = request(
                    server,
                    "GET",
                    "/metrics",
                    List.of(new Header("Authorization", "Basic " + token)),
                    false);
            assertThat(authorized.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(authorized.body).contains("secure_requests_total 1.0");
        }
    }

    @Test
    void httpMetricHandlerCanBeMountedOnCustomApplicationContext() throws Exception {
        CollectorRegistry registry = new CollectorRegistry();
        Gauge customContextGauge = Gauge.build("custom_context_metric", "Custom context metric.")
                .register(registry);
        customContextGauge.set(11.0);

        AtomicInteger threadNumber = new AtomicInteger();
        ExecutorService executorService = Executors.newSingleThreadExecutor(new NamedThreadFactory(threadNumber));
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(loopbackAddress(), 0), 3);
        httpServer.setExecutor(executorService);
        httpServer.createContext("/actuator/prometheus", new HTTPServer.HTTPMetricHandler(registry));
        httpServer.createContext("/application", exchange -> {
            byte[] body = "application response".getBytes(UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });

        try {
            httpServer.start();
            int port = httpServer.getAddress().getPort();

            HttpResponse metrics = request(port, "GET", "/actuator/prometheus", List.of(), false);
            assertThat(metrics.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(metrics.header("Content-Type")).startsWith("text/plain");
            assertThat(metrics.body)
                    .contains("# HELP custom_context_metric Custom context metric.")
                    .contains("custom_context_metric 11.0");

            HttpResponse application = request(port, "GET", "/application", List.of(), false);
            assertThat(application.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(application.body).isEqualTo("application response");
        } finally {
            httpServer.stop(0);
            executorService.shutdown();
            assertThat(executorService.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void builderUsesProvidedExecutorAndRejectsConflictingOptions() throws Exception {
        CollectorRegistry registry = new CollectorRegistry();
        Gauge executorGauge = Gauge.build("executor_backed_metric", "Executor backed metric.")
                .register(registry);
        executorGauge.set(4.0);
        AtomicInteger threadNumber = new AtomicInteger();
        ExecutorService executorService = Executors.newSingleThreadExecutor(new NamedThreadFactory(threadNumber));

        try (HTTPServer server = new HTTPServer.Builder()
                .withInetSocketAddress(new InetSocketAddress(loopbackAddress(), 0))
                .withExecutorService(executorService)
                .withRegistry(registry)
                .withDaemonThreads(true)
                .build()) {
            HttpResponse response = request(server, "GET", "/metrics", List.of(), false);
            assertThat(response.statusCode).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.body).contains("executor_backed_metric 4.0");
        }
        assertThat(executorService.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        assertThat(threadNumber.get()).isEqualTo(1);

        assertThatIllegalStateException().isThrownBy(() -> new HTTPServer.Builder()
                .withSampleNameFilter(sampleName -> true)
                .withSampleNameFilterSupplier(() -> sampleName -> true)
                .build());
        HttpServer conflictingHttpServer = HttpServer.create(
                new InetSocketAddress(loopbackAddress(), 0), 3);
        try {
            assertThatIllegalStateException().isThrownBy(() -> new HTTPServer.Builder()
                    .withHttpServer(conflictingHttpServer)
                    .withPort(1)
                    .build());
        } finally {
            conflictingHttpServer.stop(0);
        }
        assertThatIllegalStateException().isThrownBy(() -> new HTTPServer.Builder()
                .withHostname("127.0.0.1")
                .withInetAddress(loopbackAddress())
                .build());
    }

    private static InetAddress loopbackAddress() throws IOException {
        return InetAddress.getByName("127.0.0.1");
    }

    private static HttpResponse request(
            HTTPServer server,
            String method,
            String path,
            List<Header> requestHeaders,
            boolean gzip) throws IOException {
        return request(server.getPort(), method, path, requestHeaders, gzip);
    }

    private static HttpResponse request(
            int port,
            String method,
            String path,
            List<Header> requestHeaders,
            boolean gzip) throws IOException {
        URL url = URI.create("http://127.0.0.1:" + port + path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod(method);
        for (Header header : requestHeaders) {
            connection.setRequestProperty(header.name, header.value);
        }

        try {
            int statusCode = connection.getResponseCode();
            byte[] bytes = readResponseBytes(connection, statusCode, gzip);
            Map<String, List<String>> headers = connection.getHeaderFields();
            return new HttpResponse(statusCode, headers, new String(bytes, UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readResponseBytes(
            HttpURLConnection connection,
            int statusCode,
            boolean gzip) throws IOException {
        InputStream stream = statusCode >= HttpURLConnection.HTTP_BAD_REQUEST
                ? connection.getErrorStream()
                : connection.getInputStream();
        if (stream == null) {
            return new byte[0];
        }
        try (InputStream input = gzip ? new GZIPInputStream(stream) : stream;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output);
            return output.toByteArray();
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private record Header(String name, String value) {
    }

    private record HttpResponse(int statusCode, Map<String, List<String>> headers, String body) {
        private String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .flatMap(List::stream)
                    .findFirst()
                    .orElse("");
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber;

        private NamedThreadFactory(AtomicInteger threadNumber) {
            this.threadNumber = threadNumber;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "prometheus-httpserver-test-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
