/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brave.Tag;
import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.BytesEncoder;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.reporter.brave.MutableSpanBytesEncoder;
import zipkin2.reporter.okhttp3.OkHttpSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Brave tracer export through `AsyncZipkinSpanHandler`: finished Brave spans encoded straight from
 * `MutableSpan` into Proto3 or JSON v2 messages and posted to a Zipkin collector over HTTP.
 *
 * A storefront and its inventory service each trace with their own Brave `Tracing`, and each
 * exports through its own `AsyncZipkinSpanHandler`: the storefront over `OkHttpSender` in Proto3,
 * the inventory over `URLConnectionSender` in JSON v2. The storefront calls the inventory over
 * real HTTP carrying B3 headers, so the collector, a JDK `HttpServer`, receives one trace from two
 * services. The scenario also covers error tagging, a derived handler from `toBuilder()`, an
 * application `BytesEncoder<MutableSpan>` that redacts card numbers, and the handler lifecycle.
 */
public class BraveAsyncSpanExportTest {

    private static final Propagation.Setter<HttpURLConnection, String> REQUEST_HEADERS =
            HttpURLConnection::setRequestProperty;
    private static final Propagation.Getter<HttpExchange, String> EXCHANGE_HEADERS =
            (exchange, key) -> exchange.getRequestHeaders().getFirst(key);

    private HttpServer collector;
    private final BlockingQueue<Post> posts = new LinkedBlockingQueue<>();
    private String collectorEndpoint;

    @BeforeEach
    void startCollector() throws IOException {
        collector = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        collector.createContext("/api/v2/spans", this::receive);
        collector.start();
        collectorEndpoint = "http://127.0.0.1:" + collector.getAddress().getPort() + "/api/v2/spans";
    }

    @AfterEach
    void stopCollector() {
        collector.stop(0);
    }

    @Test
    void oneTraceCrossesTwoServicesAndArrivesInEachServicesEncoding() throws Exception {
        OkHttpSender storefrontSender = OkHttpSender.newBuilder()
                .endpoint(collectorEndpoint)
                .encoding(Encoding.PROTO3)
                .compressionEnabled(false)
                .build();
        URLConnectionSender inventorySender = URLConnectionSender.newBuilder()
                .endpoint(collectorEndpoint)
                .encoding(Encoding.JSON)
                .compressionEnabled(false)
                .build();
        AsyncZipkinSpanHandler storefrontHandler = AsyncZipkinSpanHandler.newBuilder(storefrontSender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        AsyncZipkinSpanHandler inventoryHandler = AsyncZipkinSpanHandler.newBuilder(inventorySender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        Tracing storefront = tracing("storefront", "10.0.0.1", 8080, storefrontHandler);
        Tracing inventory = tracing("inventory", "10.0.0.2", 9090, inventoryHandler);
        InventoryService inventoryService = new InventoryService(inventory);
        try {
            Tracer tracer = storefront.tracer();
            brave.Span client = tracer.newTrace()
                    .kind(brave.Span.Kind.CLIENT)
                    .name("GET /stock")
                    .remoteServiceName("inventory")
                    .tag("sku", "SKU-42")
                    .start();
            String answer;
            try (Tracer.SpanInScope scope = tracer.withSpanInScope(client)) {
                answer = inventoryService.stockFor(client.context(), storefront, "SKU-42");
            } finally {
                client.finish();
            }
            assertEquals("SKU-42=7", answer);

            // The B3 headers on the wire carry the storefront's trace and span ids.
            TraceContext sent = client.context();
            assertEquals(sent.traceIdString(), inventoryService.headers.get("X-B3-TraceId"));
            assertEquals(sent.spanIdString(), inventoryService.headers.get("X-B3-SpanId"));
            assertEquals("1", inventoryService.headers.get("X-B3-Sampled"));

            // Nothing leaves either service until the application flushes its handler.
            assertTrue(posts.isEmpty());
            storefrontHandler.flush();
            Post storefrontPost = nextPost();
            assertEquals("application/x-protobuf", storefrontPost.contentType);
            List<Span> storefrontSpans = SpanBytesDecoder.PROTO3.decodeList(storefrontPost.body);

            inventoryHandler.flush();
            Post inventoryPost = nextPost();
            assertEquals("application/json", inventoryPost.contentType);
            List<Span> inventorySpans = SpanBytesDecoder.JSON_V2.decodeList(inventoryPost.body);

            assertEquals(1, storefrontSpans.size());
            Span clientSpan = storefrontSpans.get(0);
            assertEquals(sent.traceIdString(), clientSpan.traceId());
            assertEquals(sent.spanIdString(), clientSpan.id());
            assertNull(clientSpan.parentId());
            assertEquals(Span.Kind.CLIENT, clientSpan.kind());
            assertEquals("get /stock", clientSpan.name());
            assertEquals("storefront", clientSpan.localServiceName());
            assertEquals("10.0.0.1", clientSpan.localEndpoint().ipv4());
            assertEquals(8080, clientSpan.localEndpoint().portAsInt());
            assertEquals("inventory", clientSpan.remoteServiceName());
            assertEquals(Map.of("sku", "SKU-42"), clientSpan.tags());
            assertFalse(Boolean.TRUE.equals(clientSpan.shared()));

            // The inventory joined the caller's span, so its server side shares the span id.
            assertEquals(2, inventorySpans.size());
            Span serverSpan = inventorySpans.stream()
                    .filter(span -> span.kind() == Span.Kind.SERVER)
                    .findFirst()
                    .orElseThrow();
            assertEquals(sent.traceIdString(), serverSpan.traceId());
            assertEquals(sent.spanIdString(), serverSpan.id());
            assertEquals(Boolean.TRUE, serverSpan.shared());
            assertEquals("inventory", serverSpan.localServiceName());
            assertEquals("10.0.0.2", serverSpan.localEndpoint().ipv4());
            assertEquals("storefront", serverSpan.remoteServiceName());
            assertEquals("7", serverSpan.tags().get("stock.level"));
            assertEquals(List.of("stock.read"), serverSpan.annotations().stream()
                    .map(annotation -> annotation.value())
                    .toList());

            // Work the inventory does locally is a child of the shared server span.
            Span lookup = inventorySpans.stream()
                    .filter(span -> span.kind() == null)
                    .findFirst()
                    .orElseThrow();
            assertEquals(sent.traceIdString(), lookup.traceId());
            assertEquals(serverSpan.id(), lookup.parentId());
            assertEquals("select stock", lookup.name());
            assertEquals("inventory", lookup.localServiceName());
            assertTrue(serverSpan.durationAsLong() >= lookup.durationAsLong());
        } finally {
            inventoryService.stop();
            storefront.close();
            inventory.close();
            storefrontHandler.close();
            inventoryHandler.close();
            storefrontSender.close();
            inventorySender.close();
        }
    }

    @Test
    void failedSpansCarryTheErrorTagTheHandlerWasBuiltWith() throws Exception {
        URLConnectionSender sender = URLConnectionSender.newBuilder()
                .endpoint(collectorEndpoint)
                .compressionEnabled(false)
                .build();
        AsyncZipkinSpanHandler defaults = AsyncZipkinSpanHandler.newBuilder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        // The payments team wants the exception type, not its message, under its own key.
        AsyncZipkinSpanHandler typed = defaults.toBuilder()
                .errorTag(new ExceptionTypeTag())
                .build();
        Tracing defaultTracing = tracing("payments", "10.0.0.3", 7070, defaults);
        Tracing typedTracing = tracing("payments", "10.0.0.3", 7070, typed);
        try {
            failCharge(defaultTracing, new IllegalStateException("card declined"));
            defaults.flush();
            Span withDefaultTag = single(SpanBytesDecoder.JSON_V2.decodeList(nextPost().body));
            assertEquals("charge", withDefaultTag.name());
            assertEquals("card declined", withDefaultTag.tags().get("error"));
            assertFalse(withDefaultTag.tags().containsKey("exception"));

            failCharge(typedTracing, new IllegalStateException("card declined"));
            typed.flush();
            Span withTypedTag = single(SpanBytesDecoder.JSON_V2.decodeList(nextPost().body));
            assertEquals("IllegalStateException", withTypedTag.tags().get("exception"));
            assertFalse(withTypedTag.tags().containsKey("error"));

            // An error tag set by the application itself wins over the handler's.
            brave.Span explicit = defaultTracing.tracer().newTrace().name("refund").start();
            explicit.tag("error", "refund window closed");
            explicit.error(new IllegalArgumentException("ignored"));
            explicit.finish();
            defaults.flush();
            Span explicitError = single(SpanBytesDecoder.JSON_V2.decodeList(nextPost().body));
            assertEquals("refund window closed", explicitError.tags().get("error"));
        } finally {
            defaultTracing.close();
            typedTracing.close();
            typed.close();
            defaults.close();
            sender.close();
        }
    }

    @Test
    void anApplicationEncoderRedactsSpansBeforeTheyLeaveTheService() throws Exception {
        URLConnectionSender jsonSender = URLConnectionSender.newBuilder()
                .endpoint(collectorEndpoint)
                .compressionEnabled(false)
                .build();
        // The encoder must speak the sender's encoding, or the handler refuses to build.
        assertThrows(IllegalArgumentException.class, () -> AsyncZipkinSpanHandler
                .newBuilder(jsonSender)
                .build(MutableSpanBytesEncoder.PROTO3));

        AsyncZipkinSpanHandler handler = AsyncZipkinSpanHandler.newBuilder(jsonSender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build(new CardRedactingEncoder());
        Tracing tracing = tracing("checkout", "10.0.0.4", 6060, handler);
        try {
            brave.Span charge = tracing.tracer().newTrace().name("authorize").start();
            charge.tag("card.number", "4111111111111111");
            charge.tag("amount", "19.99");
            charge.finish();
            handler.flush();

            Post post = nextPost();
            String json = new String(post.body, StandardCharsets.UTF_8);
            assertFalse(json.contains("4111111111111111"), json);
            Span redacted = single(SpanBytesDecoder.JSON_V2.decodeList(post.body));
            assertEquals("authorize", redacted.name());
            assertEquals(Map.of("amount", "19.99", "card.number", "****1111"), redacted.tags());
        } finally {
            tracing.close();
            handler.close();
            jsonSender.close();
        }
    }

    @Test
    void theHandlerDeliversInTheBackgroundAndDropsSpansFinishedAfterClose() throws Exception {
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        URLConnectionSender sender = URLConnectionSender.newBuilder()
                .endpoint(collectorEndpoint)
                .encoding(Encoding.PROTO3)
                .compressionEnabled(false)
                .build();
        AsyncZipkinSpanHandler handler = AsyncZipkinSpanHandler.newBuilder(sender)
                .messageTimeout(50, TimeUnit.MILLISECONDS)
                .metrics(metrics)
                .build();
        Tracing tracing = tracing("search", "10.0.0.5", 5050, handler);
        try {
            assertEquals("AsyncReporter{URLConnectionSender{" + collectorEndpoint + "}}",
                    handler.toString());

            // Without an explicit flush, the message timeout ships the span on its own.
            brave.Span query = tracing.tracer().newTrace().name("query").start();
            query.tag("q", "graalvm");
            query.finish();
            Span delivered = single(SpanBytesDecoder.PROTO3.decodeList(nextPost().body));
            assertEquals(query.context().traceIdString(), delivered.traceId());
            assertEquals("graalvm", delivered.tags().get("q"));
            assertEquals(1, metrics.spans());
            assertEquals(0, metrics.spansDropped());

            // Shutdown order: the handler first, then the sender it does not own.
            handler.close();
            handler.close();
            brave.Span late = tracing.tracer().newTrace().name("late").start();
            late.finish();
            assertEquals(1, metrics.spansDropped());
            assertThrows(ClosedSenderException.class, handler::flush);
            assertNull(posts.poll(200, TimeUnit.MILLISECONDS));
        } finally {
            tracing.close();
            handler.close();
            sender.close();
        }
    }

    private static Tracing tracing(String service, String ip, int port, AsyncZipkinSpanHandler handler) {
        return Tracing.newBuilder()
                .localServiceName(service)
                .localIp(ip)
                .localPort(port)
                .traceId128Bit(true)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .addSpanHandler(handler)
                .build();
    }

    private static void failCharge(Tracing tracing, Throwable failure) {
        brave.Span charge = tracing.tracer().newTrace().name("charge").start();
        charge.error(failure);
        charge.finish();
    }

    private static Span single(List<Span> spans) {
        assertEquals(1, spans.size(), spans.toString());
        return spans.get(0);
    }

    private Post nextPost() throws InterruptedException {
        Post post = posts.poll(10, TimeUnit.SECONDS);
        assertNotNull(post, "the collector received no spans");
        return post;
    }

    private void receive(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            posts.add(new Post(exchange.getRequestHeaders().getFirst("Content-Type"), body.readAllBytes()));
        }
        exchange.sendResponseHeaders(202, -1);
        exchange.close();
    }

    /// One message the collector received, with the media type the sender declared.
    private record Post(String contentType, byte[] body) {
    }

    /// The inventory service: joins the caller's B3 context and records its own work.
    private static final class InventoryService {

        private final Tracing tracing;
        private final HttpServer server;
        private final Map<String, String> headers = new ConcurrentHashMap<>();

        InventoryService(Tracing tracing) throws IOException {
            this.tracing = tracing;
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/stock", this::handle);
            server.start();
        }

        String stockFor(TraceContext caller, Tracing callerTracing, String sku) throws IOException {
            URL url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/stock?sku=" + sku)
                    .toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                callerTracing.propagation().injector(REQUEST_HEADERS).inject(caller, connection);
                assertEquals(200, connection.getResponseCode());
                try (InputStream in = connection.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } finally {
                connection.disconnect();
            }
        }

        private void handle(HttpExchange exchange) throws IOException {
            for (String key : tracing.propagation().keys()) {
                String value = exchange.getRequestHeaders().getFirst(key);
                if (value != null) {
                    headers.put(key, value);
                }
            }
            TraceContextOrSamplingFlags extracted =
                    tracing.propagation().extractor(EXCHANGE_HEADERS).extract(exchange);
            Tracer tracer = tracing.tracer();
            brave.Span server = tracer.joinSpan(extracted.context())
                    .kind(brave.Span.Kind.SERVER)
                    .name("GET /stock")
                    .remoteServiceName("storefront")
                    .start();
            String sku = exchange.getRequestURI().getQuery().substring("sku=".length());
            int level;
            try (Tracer.SpanInScope scope = tracer.withSpanInScope(server)) {
                brave.ScopedSpan lookup = tracer.startScopedSpan("select stock");
                try {
                    level = 7;
                    lookup.tag("db.sku", sku);
                } finally {
                    lookup.finish();
                }
                server.annotate("stock.read");
                server.tag("stock.level", Integer.toString(level));
            } finally {
                server.finish();
            }
            byte[] response = (sku + "=" + level).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        }

        void stop() {
            server.stop(0);
        }
    }

    /// Tags failures with the simple name of the exception type.
    private static final class ExceptionTypeTag extends Tag<Throwable> {

        ExceptionTypeTag() {
            super("exception");
        }

        @Override
        protected String parseValue(Throwable input, TraceContext context) {
            return input.getClass().getSimpleName();
        }
    }

    /// Masks all but the last four digits of a card number before JSON v2 encoding.
    private static final class CardRedactingEncoder implements BytesEncoder<MutableSpan> {

        private final BytesEncoder<MutableSpan> json = MutableSpanBytesEncoder.JSON_V2;

        @Override
        public Encoding encoding() {
            return Encoding.JSON;
        }

        @Override
        public int sizeInBytes(MutableSpan span) {
            return json.sizeInBytes(redacted(span));
        }

        @Override
        public byte[] encode(MutableSpan span) {
            return json.encode(redacted(span));
        }

        private static MutableSpan redacted(MutableSpan span) {
            String card = span.tag("card.number");
            if (card == null) {
                return span;
            }
            MutableSpan copy = new MutableSpan(span);
            copy.tag("card.number", "****" + card.substring(card.length() - 4));
            return copy;
        }
    }
}
