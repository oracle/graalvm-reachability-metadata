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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.HttpEndpointSupplier;
import zipkin2.reporter.HttpEndpointSuppliers;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.SpanBytesEncoder;
import zipkin2.reporter.okhttp3.OkHttpSender;

/**
 * Resolving the Zipkin collector URL through `HttpEndpointSupplier`: a custom
 * `HttpEndpointSupplier.Factory` backed by service discovery, per-message lookups by a dynamic
 * supplier, one-shot resolution of a `Constant` supplier, `HttpEndpointSuppliers` rate limiting,
 * failed lookups dropping messages, and the supplier lifecycle tied to the sender.
 *
 * A payments service does not hard-code its collector: it hands `OkHttpSender` the logical name
 * `zipkin-collector`, and a factory resolves that name against an in-process service registry
 * whose instances come and go. Two JDK `HttpServer`s play the collector instances and record
 * every POST, so each test observes which instance an operator would see the trace arrive at.
 */
public class HttpEndpointSupplierDiscoveryTest {

    private static final String SERVICE_NAME = "zipkin-collector";
    private static final String TRACE_ID = "5af7183fb1d4cf5f9a2d8c1e4b7f6031";

    private Collector primary;
    private Collector standby;
    private final ServiceRegistry registry = new ServiceRegistry();

    @BeforeEach
    void startCollectors() throws IOException {
        primary = Collector.start();
        standby = Collector.start();
    }

    @AfterEach
    void stopCollectors() {
        primary.stop();
        standby.stop();
    }

    @Test
    void dynamicSupplierResolvesTheCollectorForEveryMessage() throws Exception {
        DiscoveryFactory factory = new DiscoveryFactory(registry);
        registry.register(primary.url());
        OkHttpSender sender = OkHttpSender.newBuilder()
                .endpointSupplierFactory(factory)
                .endpoint(SERVICE_NAME)
                .encoding(Encoding.PROTO3)
                .build();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build(SpanBytesEncoder.PROTO3);
        try {
            // The sender asks the factory for a supplier of the logical name once, up front.
            assertEquals(List.of(SERVICE_NAME), factory.requestedNames);
            DiscoverySupplier supplier = factory.only();
            assertEquals(0, supplier.lookups.get());

            Span authorize = span("0000000000000001", "authorize-payment");
            reporter.report(authorize);
            reporter.flush();

            Post first = primary.posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(first);
            assertEquals("POST", first.method());
            assertEquals("application/x-protobuf", first.contentType());
            assertEquals("gzip", first.contentEncoding());
            assertEquals(List.of(authorize), SpanBytesDecoder.PROTO3.decodeList(first.body()));
            assertEquals(1, supplier.lookups.get());

            // The primary instance is retired; the next message follows the registry.
            registry.register(standby.url());
            Span capture = span("0000000000000002", "capture-payment");
            reporter.report(capture);
            reporter.flush();

            Post second = standby.posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(second);
            assertEquals(List.of(capture), SpanBytesDecoder.PROTO3.decodeList(second.body()));
            assertEquals(2, supplier.lookups.get());
            assertTrue(primary.posts.isEmpty());
            assertFalse(supplier.closed);
        } finally {
            reporter.close();
            sender.close();
        }
        assertTrue(factory.only().closed);
    }

    @Test
    void constantSupplierIsResolvedOnceAndReleasedAtBuildTime() throws Exception {
        PinnedSupplier pinned = new PinnedSupplier(primary.url());
        OkHttpSender sender = OkHttpSender.newBuilder()
                .endpointSupplierFactory(endpoint -> pinned)
                .endpoint(SERVICE_NAME)
                .build();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            // A constant endpoint is read and closed while the sender is built.
            assertEquals(1, pinned.lookups.get());
            assertTrue(pinned.closed);

            List<Span> refund = List.of(
                    span("0000000000000003", "refund-payment"),
                    span("0000000000000004", "notify-ledger"));
            refund.forEach(reporter::report);
            reporter.flush();
            reporter.report(span("0000000000000005", "audit-refund"));
            reporter.flush();

            Post batch = primary.posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(batch);
            assertEquals("application/json", batch.contentType());
            assertEquals(refund, SpanBytesDecoder.JSON_V2.decodeList(batch.body()));
            Post audit = primary.posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(audit);
            assertEquals("audit-refund", SpanBytesDecoder.JSON_V2.decodeList(audit.body()).get(0).name());
            assertEquals(1, pinned.lookups.get());
        } finally {
            reporter.close();
            sender.close();
        }
    }

    @Test
    void rateLimitedFactoryReusesTheLastLookupWithinTheInterval() throws Exception {
        DiscoveryFactory discovery = new DiscoveryFactory(registry);
        registry.register(primary.url());
        OkHttpSender sender = OkHttpSender.newBuilder()
                .endpointSupplierFactory(HttpEndpointSuppliers.newRateLimitedFactory(discovery, 3600))
                .endpoint(SERVICE_NAME)
                .encoding(Encoding.PROTO3)
                .build();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build(SpanBytesEncoder.PROTO3);
        try {
            // The rate limiter resolves eagerly, then serves its cached answer for an hour.
            DiscoverySupplier supplier = discovery.only();
            assertEquals(1, supplier.lookups.get());

            reporter.report(span("0000000000000006", "authorize-payment"));
            reporter.flush();
            registry.register(standby.url());
            reporter.report(span("0000000000000007", "capture-payment"));
            reporter.flush();

            Post first = primary.posts.poll(10, TimeUnit.SECONDS);
            Post second = primary.posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(first);
            assertNotNull(second);
            assertEquals("authorize-payment", SpanBytesDecoder.PROTO3.decodeList(first.body()).get(0).name());
            assertEquals("capture-payment", SpanBytesDecoder.PROTO3.decodeList(second.body()).get(0).name());
            assertTrue(standby.posts.isEmpty());
            assertEquals(1, supplier.lookups.get());
        } finally {
            reporter.close();
            sender.close();
        }
        // Closing the sender closes the rate limiter, which closes the discovery supplier.
        assertTrue(discovery.only().closed);
    }

    @Test
    void failedLookupDropsTheMessageUntilACollectorRegisters() throws Exception {
        DiscoveryFactory factory = new DiscoveryFactory(registry);
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        OkHttpSender sender = OkHttpSender.newBuilder()
                .endpointSupplierFactory(factory)
                .endpoint(SERVICE_NAME)
                .build();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            // No collector instance is registered yet, so the lookup fails and the batch is lost.
            reporter.report(span("0000000000000008", "authorize-payment"));
            reporter.report(span("0000000000000009", "capture-payment"));
            reporter.flush();

            assertEquals(1, metrics.messagesDropped());
            assertEquals(2, metrics.spansDropped());
            assertEquals(0, metrics.messages() - metrics.messagesDropped());
            Map<Class<? extends Throwable>, Long> causes = metrics.messagesDroppedByCause();
            assertEquals(Map.of(IllegalStateException.class, 1L), causes);
            assertTrue(primary.posts.isEmpty());

            registry.register(primary.url());
            Span settle = span("000000000000000a", "settle-payment");
            reporter.report(settle);
            reporter.flush();

            Post post = primary.posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(post);
            assertEquals(List.of(settle), SpanBytesDecoder.JSON_V2.decodeList(post.body()));
            assertEquals(1, metrics.messagesDropped());
            assertEquals(2, metrics.messages());
            assertEquals(2, factory.only().lookups.get());
        } finally {
            reporter.close();
            sender.close();
        }
    }

    private static Span span(String id, String name) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .id(id)
                .name(name)
                .kind(Span.Kind.CLIENT)
                .localEndpoint(Endpoint.newBuilder().serviceName("payments").ip("10.0.4.12").build())
                .remoteEndpoint(Endpoint.newBuilder().serviceName("card-gateway").port(8443).build())
                .timestamp(1_700_000_100_000_000L)
                .duration(4_200L)
                .putTag("payment.currency", "EUR")
                .build();
    }

    /// One POST as a collector instance received it, with the body already decompressed.
    private record Post(String method, String contentType, String contentEncoding, byte[] body) {
    }

    /// A Zipkin collector instance listening on an ephemeral loopback port.
    private record Collector(HttpServer server, BlockingQueue<Post> posts) {

        static Collector start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            Collector collector = new Collector(server, new LinkedBlockingQueue<>());
            server.createContext("/api/v2/spans", collector::receive);
            server.start();
            return collector;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2/spans";
        }

        void stop() {
            server.stop(0);
        }

        private void receive(HttpExchange exchange) throws IOException {
            String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
            byte[] body;
            try (InputStream raw = exchange.getRequestBody();
                    InputStream in = "gzip".equals(encoding) ? new GZIPInputStream(raw) : raw) {
                body = in.readAllBytes();
            }
            posts.add(new Post(exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"), encoding, body));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        }
    }

    /// The service registry the payments service discovers its collector through.
    private static final class ServiceRegistry {

        private volatile String collectorUrl;

        void register(String url) {
            collectorUrl = url;
        }

        String collectorUrl() {
            return collectorUrl;
        }
    }

    /// Creates one discovery-backed supplier per logical service name the sender asks for.
    private static final class DiscoveryFactory implements HttpEndpointSupplier.Factory {

        private final ServiceRegistry registry;
        private final List<String> requestedNames = new CopyOnWriteArrayList<>();
        private final List<DiscoverySupplier> suppliers = new CopyOnWriteArrayList<>();

        DiscoveryFactory(ServiceRegistry registry) {
            this.registry = registry;
        }

        @Override
        public HttpEndpointSupplier create(String endpoint) {
            requestedNames.add(endpoint);
            DiscoverySupplier supplier = new DiscoverySupplier(registry, endpoint);
            suppliers.add(supplier);
            return supplier;
        }

        DiscoverySupplier only() {
            assertEquals(1, suppliers.size());
            return suppliers.get(0);
        }
    }

    /// Looks the collector up in the registry each time the sender needs an endpoint.
    private static final class DiscoverySupplier implements HttpEndpointSupplier {

        private final ServiceRegistry registry;
        private final String serviceName;
        private final AtomicInteger lookups = new AtomicInteger();
        private volatile boolean closed;

        DiscoverySupplier(ServiceRegistry registry, String serviceName) {
            this.registry = registry;
            this.serviceName = serviceName;
        }

        @Override
        public String get() {
            lookups.incrementAndGet();
            String url = registry.collectorUrl();
            if (url == null) {
                throw new IllegalStateException("no " + serviceName + " instance registered");
            }
            return url;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /// A collector URL pinned in configuration, which the sender may resolve once and cache.
    private static final class PinnedSupplier implements HttpEndpointSupplier.Constant {

        private final String url;
        private final AtomicInteger lookups = new AtomicInteger();
        private volatile boolean closed;

        PinnedSupplier(String url) {
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
}
