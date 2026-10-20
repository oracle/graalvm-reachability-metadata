/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.ServletConnection;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http2.Http2Protocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serves a telemetry dashboard over HTTP/2 on a cleartext connector, where an
 * `Http2Protocol` upgrade protocol accepts the `h2c` upgrade, multiplexes concurrent
 * streams on one connection, applies connector compression and flow-controls large bodies.
 *
 * A monitoring backend lets its dashboard poll many widgets at once: browsers and agents
 * that offer `Upgrade: h2c` are moved to HTTP/2 and then fetch every widget as a separate
 * stream on a single TCP connection, while plain HTTP/1.1 clients keep working on the same
 * port. Servlets see the negotiated protocol through `ServletConnection`. The CSV export is
 * larger than the default HTTP/2 flow-control window and leaves the server gzip-compressed,
 * and metric samples are pushed to the backend in a body that spans many DATA frames.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelemetryDashboardHttp2Test {

    private static final List<String> WIDGETS = List.of("cpu", "memory", "disk", "network", "latency", "errors");

    private static final int EXPORT_ROWS = 12_000;

    private Tomcat tomcat;

    private Path baseDir;

    private final CountDownLatch liveWidgets = new CountDownLatch(WIDGETS.size());

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("telemetry-h2-tomcat");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);

        Connector connector = tomcat.getConnector();
        Http2Protocol http2 = new Http2Protocol();
        http2.setMaxConcurrentStreams(32);
        http2.setInitialWindowSize(65_535);
        connector.addUpgradeProtocol(http2);
        connector.setProperty("compression", "on");
        connector.setProperty("compressionMinSize", "2048");
        connector.setProperty("compressibleMimeType", "text/csv,application/json");

        Context context = tomcat.addContext("/telemetry", baseDir.toString());
        Tomcat.addServlet(context, "widgets", new WidgetServlet());
        context.addServletMappingDecoded("/widgets/*", "widgets");
        Tomcat.addServlet(context, "live", new LiveWidgetServlet());
        context.addServletMappingDecoded("/live/*", "live");
        Tomcat.addServlet(context, "export", new ExportServlet());
        context.addServletMappingDecoded("/export.csv", "export");
        Tomcat.addServlet(context, "samples", new SampleIngestServlet());
        context.addServletMappingDecoded("/samples", "samples");

        tomcat.start();
        baseUrl = "http://localhost:" + connector.getLocalPort() + "/telemetry";
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void upgradesACleartextConnectionToHttp2() throws Exception {
        HttpResponse<String> response = send(http2Client(), get("/widgets/cpu"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
        Map<String, String> fields = fields(response.body());
        assertThat(fields).containsEntry("widget", "cpu")
                .containsEntry("requestProtocol", "HTTP/2.0")
                .containsEntry("connectionProtocol", "h2c");
    }

    @Test
    void keepsServingHttp11ClientsOnTheSamePort() throws Exception {
        HttpClient http11 = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = send(http11, get("/widgets/memory"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        Map<String, String> fields = fields(response.body());
        assertThat(fields).containsEntry("widget", "memory")
                .containsEntry("requestProtocol", "HTTP/1.1")
                .containsEntry("connectionProtocol", "http/1.1");
    }

    @Test
    void multiplexesDashboardWidgetsAsStreamsOfOneConnection() throws Exception {
        HttpClient client = http2Client();
        HttpResponse<String> first = send(client, get("/widgets/overview"));
        String connectionId = fields(first.body()).get("connectionId");

        List<CompletableFuture<HttpResponse<String>>> pending = new ArrayList<>();
        for (String widget : WIDGETS) {
            pending.add(client.sendAsync(get("/live/" + widget).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        }

        Set<String> widgets = new HashSet<>();
        for (CompletableFuture<HttpResponse<String>> future : pending) {
            HttpResponse<String> response = future.get(20, TimeUnit.SECONDS);
            assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
            Map<String, String> fields = fields(response.body());
            assertThat(fields.get("connectionId")).isEqualTo(connectionId);
            assertThat(fields).containsEntry("allWidgetsInFlight", "true")
                    .containsEntry("requestProtocol", "HTTP/2.0");
            widgets.add(fields.get("widget"));
        }

        assertThat(widgets).containsExactlyInAnyOrderElementsOf(WIDGETS);
    }

    @Test
    void streamsACompressedExportLargerThanTheFlowControlWindow() throws Exception {
        HttpClient client = http2Client();
        send(client, get("/widgets/overview"));

        HttpResponse<byte[]> response = client.send(get("/export.csv")
                        .header("Accept-Encoding", "gzip")
                        .timeout(Duration.ofSeconds(20))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(response.headers().firstValue("Content-Encoding")).hasValue("gzip");
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(response.body())), StandardCharsets.UTF_8))) {
            lines = reader.lines().collect(Collectors.toList());
        }
        int uncompressed = lines.stream().mapToInt(line -> line.length() + 1).sum();
        assertThat(uncompressed).isGreaterThan(65_535);
        assertThat(response.body().length).isLessThan(uncompressed);
        assertThat(lines).hasSize(EXPORT_ROWS + 1);
        assertThat(lines.get(0)).isEqualTo("minute,widget,value");
        assertThat(lines.get(1)).isEqualTo("0,cpu,0");
        assertThat(lines.get(EXPORT_ROWS)).isEqualTo(exportRow(EXPORT_ROWS - 1));
    }

    @Test
    void ingestsMetricSamplesSentAsManyDataFrames() throws Exception {
        HttpClient client = http2Client();
        send(client, get("/widgets/overview"));
        StringBuilder samples = new StringBuilder();
        long expectedSum = 0;
        for (int i = 0; i < 30_000; i++) {
            int value = i % 997;
            samples.append("latency.p99 ").append(value).append('\n');
            expectedSum += value;
        }
        byte[] body = samples.toString().getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = send(client, HttpRequest.newBuilder(URI.create(baseUrl + "/samples"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(body.length).isGreaterThan(65_535);
        assertThat(fields(response.body())).containsEntry("samples", "30000")
                .containsEntry("sum", Long.toString(expectedSum))
                .containsEntry("requestProtocol", "HTTP/2.0");
    }

    private static HttpClient http2Client() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request)
            throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Parses the `key=value` lines the dashboard servlets answer with. */
    private static Map<String, String> fields(String body) {
        return body.lines()
                .map(line -> line.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
    }

    private static String exportRow(int row) {
        return row / WIDGETS.size() + "," + WIDGETS.get(row % WIDGETS.size()) + "," + (row * 7 % 1000);
    }

    private static void describeConnection(HttpServletRequest request, PrintWriter writer) {
        ServletConnection connection = request.getServletConnection();
        writer.println("requestProtocol=" + request.getProtocol());
        writer.println("connectionProtocol=" + connection.getProtocol());
        writer.println("connectionId=" + connection.getConnectionId());
    }

    /** Answers one dashboard widget and the connection details it was served on. */
    static final class WidgetServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            PrintWriter writer = response.getWriter();
            writer.println("widget=" + request.getPathInfo().substring(1));
            describeConnection(request, writer);
        }
    }

    /**
     * Answers a live widget only once every live widget request is in flight, which an
     * HTTP/2 connection can only satisfy by running the streams concurrently.
     */
    final class LiveWidgetServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            liveWidgets.countDown();
            boolean allInFlight;
            try {
                allInFlight = liveWidgets.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                allInFlight = false;
            }
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            PrintWriter writer = response.getWriter();
            writer.println("widget=" + request.getPathInfo().substring(1));
            writer.println("allWidgetsInFlight=" + allInFlight);
            describeConnection(request, writer);
        }
    }

    /** Streams the per-minute widget history as CSV. */
    static final class ExportServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/csv");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            PrintWriter writer = response.getWriter();
            writer.print("minute,widget,value\n");
            for (int row = 0; row < EXPORT_ROWS; row++) {
                writer.print(exportRow(row));
                writer.print('\n');
            }
        }
    }

    /** Totals `name value` metric samples pushed by a monitoring agent. */
    static final class SampleIngestServlet extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            long count = 0;
            long sum = 0;
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    count++;
                    sum += Long.parseLong(line.substring(line.indexOf(' ') + 1));
                }
            }
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            PrintWriter writer = response.getWriter();
            writer.println("samples=" + count);
            writer.println("sum=" + sum);
            describeConnection(request, writer);
        }
    }
}
