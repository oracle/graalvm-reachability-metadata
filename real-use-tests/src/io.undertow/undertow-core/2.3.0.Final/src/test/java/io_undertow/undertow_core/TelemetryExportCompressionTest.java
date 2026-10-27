/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.conduits.GzipStreamSourceConduit;
import io.undertow.conduits.InflatingStreamSourceConduit;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.server.handlers.encoding.RequestEncodingHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP content encoding with `EncodingHandler` and `RequestEncodingHandler`: gzip and deflate
 * response compression negotiated from `Accept-Encoding` quality values and provider priority,
 * predicates that keep small bodies and images uncompressed, and transparent decoding of gzip and
 * deflate request bodies.
 *
 * A telemetry service exports a CSV of metric samples, answers a tiny status probe, serves a
 * dashboard PNG, and ingests sample batches that agents upload compressed. The client is the JDK
 * `HttpClient`, which never decompresses on its own, so every assertion is on the encoded bytes
 * that cross the wire and on what they decode to.
 */
public class TelemetryExportCompressionTest {

    private static final int MIN_COMPRESSED_SIZE = 512;

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startServer() {
        TelemetryService service = new TelemetryService();
        HttpHandler routes = Handlers.routing()
                .get("/export.csv", service::export)
                .get("/status", service::status)
                .get("/dashboard.png", service::dashboard)
                .post("/ingest", service::ingest);

        Predicate worthCompressing = Predicates.and(
                Predicates.requestLargerThan(MIN_COMPRESSED_SIZE),
                exchange -> !contentType(exchange).startsWith("image/"));
        ContentEncodingRepository encodings = new ContentEncodingRepository()
                .addEncodingHandler("gzip", new GzipEncodingProvider(), 100, worthCompressing)
                .addEncodingHandler("deflate", new DeflateEncodingProvider(), 50, worthCompressing);
        HttpHandler compressing = new EncodingHandler(routes, encodings);

        HttpHandler decoding = new RequestEncodingHandler(compressing)
                .addEncoding("gzip", GzipStreamSourceConduit.WRAPPER)
                .addEncoding("deflate", InflatingStreamSourceConduit.WRAPPER);

        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(decoding)
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void compressesTheExportWithGzipWhenTheClientAcceptsIt() throws Exception {
        HttpResponse<byte[]> response = get("/export.csv", "gzip");

        assertEquals(200, response.statusCode());
        assertEquals("gzip", contentEncoding(response));
        assertEquals("text/csv; charset=UTF-8", response.headers().firstValue("Content-Type").orElseThrow());
        byte[] expected = TelemetryService.exportCsv().getBytes(StandardCharsets.UTF_8);
        assertTrue(response.body().length < expected.length / 4,
                "a repetitive CSV must shrink well below its size, got " + response.body().length);
        assertArrayEquals(expected, gunzip(response.body()));
    }

    @Test
    void negotiatesTheEncodingFromQualityValuesAndPriority() throws Exception {
        String csv = TelemetryService.exportCsv();

        HttpResponse<byte[]> deflatePreferred = get("/export.csv", "gzip;q=0.2, deflate;q=0.9");
        assertEquals("deflate", contentEncoding(deflatePreferred));
        assertEquals(csv, new String(inflate(deflatePreferred.body()), StandardCharsets.UTF_8));

        HttpResponse<byte[]> tie = get("/export.csv", "deflate, gzip");
        assertEquals("gzip", contentEncoding(tie), "equal quality falls back to provider priority");
        assertEquals(csv, new String(gunzip(tie.body()), StandardCharsets.UTF_8));

        HttpResponse<byte[]> qualityBeatsPriority = get("/export.csv", "deflate, gzip;q=0.5");
        assertEquals("deflate", contentEncoding(qualityBeatsPriority));
        assertEquals(csv, new String(inflate(qualityBeatsPriority.body()), StandardCharsets.UTF_8));

        HttpResponse<byte[]> gzipRefused = get("/export.csv", "gzip;q=0");
        assertEquals("", contentEncoding(gzipRefused));
        assertEquals(csv, new String(gzipRefused.body(), StandardCharsets.UTF_8));

        HttpResponse<byte[]> unsupported = get("/export.csv", "br");
        assertEquals("", contentEncoding(unsupported));
        assertEquals(csv, new String(unsupported.body(), StandardCharsets.UTF_8));

        HttpResponse<byte[]> plain = get("/export.csv", null);
        assertEquals("", contentEncoding(plain));
        assertEquals(csv, new String(plain.body(), StandardCharsets.UTF_8));
    }

    @Test
    void leavesSmallBodiesAndImagesUncompressed() throws Exception {
        HttpResponse<byte[]> status = get("/status", "gzip, deflate");
        assertEquals(200, status.statusCode());
        assertEquals("", contentEncoding(status));
        assertEquals("{\"status\":\"UP\"}", new String(status.body(), StandardCharsets.UTF_8));

        HttpResponse<byte[]> image = get("/dashboard.png", "gzip, deflate");
        assertEquals(200, image.statusCode());
        assertEquals("image/png", image.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("", contentEncoding(image));
        byte[] png = TelemetryService.dashboardPng();
        assertTrue(png.length > MIN_COMPRESSED_SIZE);
        assertArrayEquals(png, image.body());
    }

    @Test
    void decodesCompressedUploadsBeforeTheHandlerReadsThem() throws Exception {
        String batch = """
                cpu.load,web-1,0.75
                cpu.load,web-2,1.25
                mem.used,web-1,512
                """;
        byte[] raw = batch.getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> gzipped = post("/ingest", "gzip", gzip(raw));
        assertEquals(200, gzipped.statusCode());
        assertEquals("accepted 3 samples, total 514.0", gzipped.body());

        HttpResponse<String> deflated = post("/ingest", "deflate", deflate(raw));
        assertEquals(200, deflated.statusCode());
        assertEquals("accepted 3 samples, total 514.0", deflated.body());

        HttpResponse<String> uncompressed = post("/ingest", null, raw);
        assertEquals("accepted 3 samples, total 514.0", uncompressed.body());

        byte[] shortLine = "cpu.load,web-1\n".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> malformed = post("/ingest", null, shortLine);
        assertEquals(400, malformed.statusCode());
        assertEquals("line 1 must have 3 fields", malformed.body());
    }

    @Test
    void decodesALargeGzipUploadAndCompressesTheLargeReply() throws Exception {
        StringBuilder batch = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            batch.append("requests.count,web-").append(i % 4).append(",1\n");
        }
        byte[] raw = batch.toString().getBytes(StandardCharsets.UTF_8);
        byte[] compressed = gzip(raw);
        assertTrue(compressed.length < raw.length / 10);

        HttpRequest request = HttpRequest.newBuilder(base.resolve("/ingest?echo=true"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Encoding", "gzip")
                .header("Accept-Encoding", "gzip")
                .POST(HttpRequest.BodyPublishers.ofByteArray(compressed))
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertEquals("gzip", contentEncoding(response));
        String echoed = new String(gunzip(response.body()), StandardCharsets.UTF_8);
        assertTrue(echoed.startsWith("accepted 2000 samples, total 2000.0\n"), echoed.substring(0, 60));
        assertEquals(batch.toString(), echoed.substring(echoed.indexOf('\n') + 1));
    }

    private HttpResponse<byte[]> get(String path, String acceptEncoding) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30)).GET();
        if (acceptEncoding != null) {
            request.header("Accept-Encoding", acceptEncoding);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> post(String path, String contentEncoding, byte[] body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/csv")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentEncoding != null) {
            request.header("Content-Encoding", contentEncoding);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String contentEncoding(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Encoding").orElse("");
    }

    private static String contentType(HttpServerExchange exchange) {
        String value = exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
        return value == null ? "" : value;
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(bytes)) {
            out.write(data);
        }
        return bytes.toByteArray();
    }

    /// Undertow speaks `deflate` as a raw DEFLATE stream without the zlib wrapper, in both directions.
    private static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = new DeflaterOutputStream(bytes, new Deflater(Deflater.DEFAULT_COMPRESSION, true))) {
            out.write(data);
        }
        return bytes.toByteArray();
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        }
    }

    private static byte[] inflate(byte[] data) throws IOException {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(data), new Inflater(true))) {
            return in.readAllBytes();
        }
    }

    /// The telemetry endpoints; they know nothing about compression, which the handler chain adds.
    static final class TelemetryService {

        static String exportCsv() {
            StringBuilder csv = new StringBuilder("metric,host,minute,value\n");
            for (int minute = 0; minute < 120; minute++) {
                for (int host = 1; host <= 3; host++) {
                    csv.append("http.requests,web-").append(host).append(',').append(minute)
                            .append(',').append((minute * 7 + host * 13) % 50).append('\n');
                }
            }
            return csv.toString();
        }

        static byte[] dashboardPng() {
            byte[] png = new byte[2048];
            byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
            System.arraycopy(signature, 0, png, 0, signature.length);
            long state = 42;
            for (int i = signature.length; i < png.length; i++) {
                state = state * 6364136223846793005L + 1442695040888963407L;
                png[i] = (byte) (state >>> 56);
            }
            return png;
        }

        void export(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/csv; charset=UTF-8");
            exchange.getResponseSender().send(exportCsv(), StandardCharsets.UTF_8);
        }

        void status(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send("{\"status\":\"UP\"}");
        }

        void dashboard(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "image/png");
            exchange.getResponseSender().send(ByteBuffer.wrap(dashboardPng()));
        }

        void ingest(HttpServerExchange exchange) {
            boolean echo = exchange.getQueryParameters().containsKey("echo");
            exchange.getRequestReceiver().receiveFullString((ex, body) -> {
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
                String[] lines = body.lines().toArray(String[]::new);
                double total = 0;
                for (int i = 0; i < lines.length; i++) {
                    String[] fields = lines[i].split(",");
                    if (fields.length != 3) {
                        ex.setStatusCode(StatusCodes.BAD_REQUEST);
                        ex.getResponseSender().send("line " + (i + 1) + " must have 3 fields");
                        return;
                    }
                    total += Double.parseDouble(fields[2]);
                }
                String summary = "accepted " + lines.length + " samples, total " + total;
                ex.getResponseSender().send(echo ? summary + "\n" + body : summary, StandardCharsets.UTF_8);
            }, StandardCharsets.UTF_8);
        }
    }
}
