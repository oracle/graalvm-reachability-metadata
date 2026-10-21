/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.BaseHttpSender;
import zipkin2.reporter.BytesEncoder;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.HttpEndpointSupplier;
import zipkin2.reporter.HttpEndpointSuppliers;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.SpanBytesEncoder;

/**
 * An application-defined span transport built on `BaseHttpSender` and `BytesEncoder`: a sender
 * subclass over the JDK `HttpClient`, `Encoding` and `BytesMessageEncoder` message framing for
 * JSON, Thrift and Proto3, an `AsyncReporter` over the application's own event type, collector
 * rejections surfacing as the sender's exception, and the sender's close lifecycle.
 *
 * A checkout service ships its traces without OkHttp or `HttpURLConnection`: it writes a small
 * `JdkHttpSender` against the reporter's HTTP sender template and reports both Zipkin spans and
 * its own checkout audit events through it. A JDK `HttpServer` plays the collector and records
 * every POST, so each test observes the request an operator's collector would receive.
 */
public class CustomHttpSenderTransportTest {

    private static final String TRACE_ID = "7c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f";

    private Collector collector;

    @BeforeEach
    void startCollector() throws IOException {
        collector = Collector.start();
    }

    @AfterEach
    void stopCollector() {
        collector.stop();
    }

    @Test
    void zipkinSpansArePostedInEachEncodingTheSenderIsBuiltFor() throws Exception {
        List<Span> checkout = List.of(
                span("0000000000000001", "reserve-stock"),
                span("0000000000000002", "charge-card"));

        // JSON v2 is the default wire format of the collector's /api/v2/spans endpoint.
        try (JdkHttpSender sender = new JdkHttpSender(Encoding.JSON, collector.url())) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .build(SpanBytesEncoder.JSON_V2);
            checkout.forEach(reporter::report);
            reporter.flush();
            reporter.close();
        }
        Post json = collector.next();
        assertEquals("application/json", json.contentType());
        assertEquals(checkout, SpanBytesDecoder.JSON_V2.decodeList(json.body()));

        try (JdkHttpSender sender = new JdkHttpSender(Encoding.PROTO3, collector.url())) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .build(SpanBytesEncoder.PROTO3);
            checkout.forEach(reporter::report);
            reporter.flush();
            reporter.close();
        }
        Post proto = collector.next();
        assertEquals("application/x-protobuf", proto.contentType());
        assertEquals(checkout, SpanBytesDecoder.PROTO3.decodeList(proto.body()));

        // Legacy collectors still accept the v1 Thrift list; the v1 model keeps names and ids.
        try (JdkHttpSender sender = new JdkHttpSender(Encoding.THRIFT, collector.url())) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .build(SpanBytesEncoder.THRIFT);
            checkout.forEach(reporter::report);
            reporter.flush();
            reporter.close();
        }
        Post thrift = collector.next();
        assertEquals("application/x-thrift", thrift.contentType());
        List<Span> decoded = SpanBytesDecoder.THRIFT.decodeList(thrift.body());
        assertEquals(2, decoded.size());
        for (int i = 0; i < checkout.size(); i++) {
            assertEquals(checkout.get(i).traceId(), decoded.get(i).traceId());
            assertEquals(checkout.get(i).id(), decoded.get(i).id());
            assertEquals(checkout.get(i).name(), decoded.get(i).name());
            assertEquals(checkout.get(i).duration(), decoded.get(i).duration());
        }
        assertTrue(collector.posts.isEmpty());
    }

    @Test
    void applicationEventsAreReportedThroughTheirOwnEncoder() throws Exception {
        CheckoutEventEncoder encoder = new CheckoutEventEncoder();
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        List<CheckoutEvent> events = List.of(
                new CheckoutEvent("order-1042", "cart-locked", 1_700_000_200_000L),
                new CheckoutEvent("order-1042", "payment-authorized", 1_700_000_200_850L),
                new CheckoutEvent("order-1042", "receipt-sent", 1_700_000_201_400L));

        String expectedBody = """
                [{"orderId":"order-1042","step":"cart-locked","atMicros":1700000200000},\
                {"orderId":"order-1042","step":"payment-authorized","atMicros":1700000200850},\
                {"orderId":"order-1042","step":"receipt-sent","atMicros":1700000201400}]""";
        try (JdkHttpSender sender = new JdkHttpSender(Encoding.JSON, collector.url())) {
            AsyncReporter<CheckoutEvent> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .metrics(metrics)
                    .build(encoder);
            events.forEach(reporter::report);
            reporter.flush();
            reporter.close();

            Post post = collector.next();
            assertEquals("application/json", post.contentType());
            assertEquals(expectedBody, new String(post.body(), StandardCharsets.UTF_8));

            // The sender sizes a message exactly as the JSON list it frames the events into.
            List<byte[]> encoded = events.stream().map(encoder::encode).toList();
            assertEquals(post.body().length, sender.messageSizeInBytes(encoded));
            int largest = encoded.stream().mapToInt(bytes -> bytes.length).max().orElseThrow();
            assertEquals(largest + 2, sender.messageSizeInBytes(largest));
        }
        assertEquals(1, metrics.messages());
        assertEquals(3, metrics.spans());
        assertEquals(expectedBody.length() - 4, metrics.spanBytes());
        assertEquals(0, metrics.spansDropped());
    }

    @Test
    void preEncodedSpansAreFramedPerEncodingForAForwardedMessage() {
        Span reserve = span("0000000000000003", "reserve-stock");
        Span charge = span("0000000000000004", "charge-card");

        // An edge relay holds spans already encoded one by one and frames them into one message.
        List<byte[]> json = List.of(SpanBytesEncoder.JSON_V2.encode(reserve), SpanBytesEncoder.JSON_V2.encode(charge));
        byte[] jsonMessage = BytesMessageEncoder.forEncoding(Encoding.JSON).encode(json);
        assertEquals('[', jsonMessage[0]);
        assertEquals(']', jsonMessage[jsonMessage.length - 1]);
        assertEquals(Encoding.JSON.listSizeInBytes(json), jsonMessage.length);
        assertArrayEquals(jsonMessage, Encoding.JSON.encode(json));
        assertEquals(List.of(reserve, charge), SpanBytesDecoder.JSON_V2.decodeList(jsonMessage));

        List<byte[]> proto = List.of(SpanBytesEncoder.PROTO3.encode(reserve), SpanBytesEncoder.PROTO3.encode(charge));
        byte[] protoMessage = BytesMessageEncoder.PROTO3.encode(proto);
        assertEquals(proto.get(0).length + proto.get(1).length, protoMessage.length);
        assertEquals(Encoding.PROTO3.listSizeInBytes(proto), protoMessage.length);
        assertEquals(List.of(reserve, charge), SpanBytesDecoder.PROTO3.decodeList(protoMessage));

        List<byte[]> thrift = List.of(SpanBytesEncoder.THRIFT.encode(reserve), SpanBytesEncoder.THRIFT.encode(charge));
        byte[] thriftMessage = BytesMessageEncoder.THRIFT.encode(thrift);
        assertEquals(Encoding.THRIFT.listSizeInBytes(thrift), thriftMessage.length);
        List<Span> relayed = SpanBytesDecoder.THRIFT.decodeList(thriftMessage);
        assertEquals(List.of("reserve-stock", "charge-card"), relayed.stream().map(Span::name).toList());
        assertEquals("application/x-thrift", Encoding.THRIFT.mediaType());
    }

    @Test
    void collectorRejectionDropsTheMessageWithTheSendersException() throws Exception {
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        collector.rejectNext(503);
        try (JdkHttpSender sender = new JdkHttpSender(Encoding.JSON, collector.url())) {
            AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                    .messageTimeout(0, TimeUnit.MILLISECONDS)
                    .metrics(metrics)
                    .build(SpanBytesEncoder.JSON_V2);
            reporter.report(span("0000000000000005", "reserve-stock"));
            reporter.report(span("0000000000000006", "charge-card"));
            reporter.flush();

            Post rejected = collector.next();
            assertEquals(2, SpanBytesDecoder.JSON_V2.decodeList(rejected.body()).size());
            assertEquals(1, metrics.messagesDropped());
            assertEquals(2, metrics.spansDropped());
            Map<Class<? extends Throwable>, Long> causes = metrics.messagesDroppedByCause();
            assertEquals(Map.of(CollectorRejectedException.class, 1L), causes);

            // The next message reaches a healthy collector again.
            Span retry = span("0000000000000007", "charge-card-retry");
            reporter.report(retry);
            reporter.flush();
            reporter.close();
            assertEquals(List.of(retry), SpanBytesDecoder.JSON_V2.decodeList(collector.next().body()));
            assertEquals(1, metrics.messagesDropped());
            assertEquals(2, metrics.messages());

            // Sending directly, the application sees the status the collector answered with.
            collector.rejectNext(413);
            List<byte[]> oversized = List.of(SpanBytesEncoder.JSON_V2.encode(retry));
            CollectorRejectedException direct = assertThrows(CollectorRejectedException.class,
                    () -> sender.send(oversized));
            assertEquals(413, direct.status);
            collector.next();
        }
    }

    @Test
    void senderResolvesItsEndpointOnceAndReleasesItOnClose() throws Exception {
        CountingSupplier supplier = new CountingSupplier(collector.url());
        JdkHttpSender sender = new JdkHttpSender(Encoding.PROTO3, endpoint -> supplier, "zipkin");

        // A constant supplier is resolved into the sender's URI while it is built.
        assertEquals(1, supplier.lookups.get());
        assertTrue(supplier.closed);
        assertEquals("JdkHttpSender{" + collector.url() + "}", sender.toString());
        assertEquals(512 * 1024, sender.messageMaxBytes());
        assertEquals(Encoding.PROTO3, sender.encoding());

        List<byte[]> encoded = List.of(SpanBytesEncoder.PROTO3.encode(span("0000000000000008", "ship-order")));
        sender.send(encoded);
        sender.send(encoded);
        assertEquals("application/x-protobuf", collector.next().contentType());
        assertEquals("ship-order", SpanBytesDecoder.PROTO3.decodeList(collector.next().body()).get(0).name());
        assertEquals(1, supplier.lookups.get());
        assertEquals(2, sender.posted.get());

        assertFalse(sender.released);
        sender.close();
        sender.close();
        assertTrue(sender.released);
        assertEquals(1, sender.releases.get());
        assertThrows(ClosedSenderException.class, () -> sender.send(encoded));
        assertTrue(collector.posts.isEmpty());

        // The sender's own endpoint validation rejects a URL it cannot post to.
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> new JdkHttpSender(Encoding.JSON, "ftp://collector.internal/api/v2/spans"));
        assertEquals("collector endpoint must be http or https: ftp://collector.internal/api/v2/spans",
                invalid.getMessage());
    }

    private static Span span(String id, String name) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .id(id)
                .name(name)
                .kind(Span.Kind.CLIENT)
                .localEndpoint(Endpoint.newBuilder().serviceName("checkout").ip("10.0.7.21").build())
                .remoteEndpoint(Endpoint.newBuilder().serviceName("inventory").port(9090).build())
                .timestamp(1_700_000_300_000_000L)
                .duration(3_100L)
                .putTag("checkout.region", "eu-west")
                .build();
    }

    /// The checkout service's span transport: posts each message with the JDK HTTP client.
    private static final class JdkHttpSender extends BaseHttpSender<URI, byte[]> {

        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        private final AtomicInteger posted = new AtomicInteger();
        private final AtomicInteger releases = new AtomicInteger();
        private volatile boolean released;

        JdkHttpSender(Encoding encoding, String endpoint) {
            this(encoding, HttpEndpointSuppliers.constantFactory(), endpoint);
        }

        JdkHttpSender(Encoding encoding, HttpEndpointSupplier.Factory factory, String endpoint) {
            super(encoding, factory, endpoint);
        }

        @Override
        protected URI newEndpoint(String endpoint) {
            URI uri = URI.create(endpoint);
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                throw new IllegalArgumentException("collector endpoint must be http or https: " + endpoint);
            }
            return uri;
        }

        @Override
        protected byte[] newBody(List<byte[]> encodedSpans) {
            return encoding.encode(encodedSpans);
        }

        @Override
        protected void postSpans(URI endpoint, byte[] body) throws IOException {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", encoding.mediaType())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<Void> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted posting spans", e);
            }
            posted.incrementAndGet();
            if (response.statusCode() / 100 != 2) {
                throw new CollectorRejectedException(response.statusCode());
            }
        }

        @Override
        protected void doClose() {
            releases.incrementAndGet();
            released = true;
        }
    }

    /// The collector answered a POST with a non-2xx status.
    private static final class CollectorRejectedException extends IOException {

        private final int status;

        CollectorRejectedException(int status) {
            super("collector responded " + status);
            this.status = status;
        }
    }

    /// One step of a checkout, audited alongside the trace.
    private record CheckoutEvent(String orderId, String step, long atMicros) {
    }

    /// Writes a checkout event as one compact JSON object.
    private static final class CheckoutEventEncoder implements BytesEncoder<CheckoutEvent> {

        @Override
        public Encoding encoding() {
            return Encoding.JSON;
        }

        @Override
        public int sizeInBytes(CheckoutEvent event) {
            return encode(event).length;
        }

        @Override
        public byte[] encode(CheckoutEvent event) {
            String json = "{\"orderId\":\"" + event.orderId() + "\",\"step\":\"" + event.step()
                    + "\",\"atMicros\":" + event.atMicros() + "}";
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }

    /// A pinned collector URL that counts how often the sender reads it.
    private static final class CountingSupplier implements HttpEndpointSupplier.Constant {

        private final String url;
        private final AtomicInteger lookups = new AtomicInteger();
        private volatile boolean closed;

        CountingSupplier(String url) {
            this.url = url;
        }

        @Override
        public String get() {
            lookups.incrementAndGet();
            return url;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /// One POST as the collector received it.
    private record Post(String contentType, byte[] body) {
    }

    /// A Zipkin collector on an ephemeral loopback port that can reject the next POST.
    private static final class Collector {

        private final HttpServer server;
        private final BlockingQueue<Post> posts = new LinkedBlockingQueue<>();
        private final AtomicInteger rejectStatus = new AtomicInteger();

        private Collector(HttpServer server) {
            this.server = server;
        }

        static Collector start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            Collector collector = new Collector(server);
            server.createContext("/api/v2/spans", collector::receive);
            server.start();
            return collector;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2/spans";
        }

        void rejectNext(int status) {
            rejectStatus.set(status);
        }

        Post next() throws InterruptedException {
            Post post = posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(post);
            return post;
        }

        void stop() {
            server.stop(0);
        }

        private void receive(HttpExchange exchange) throws IOException {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = exchange.getRequestBody()) {
                in.transferTo(body);
            }
            posts.add(new Post(exchange.getRequestHeaders().getFirst("Content-Type"), body.toByteArray()));
            int status = rejectStatus.getAndSet(0);
            exchange.sendResponseHeaders(status == 0 ? 202 : status, -1);
            exchange.close();
        }
    }
}
