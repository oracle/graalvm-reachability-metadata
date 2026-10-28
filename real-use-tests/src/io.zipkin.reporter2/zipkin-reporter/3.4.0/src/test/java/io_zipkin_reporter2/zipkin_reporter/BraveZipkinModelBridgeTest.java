/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brave.Tag;
import brave.Tracer;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

/**
 * The in-process Brave-to-zipkin2 model bridge: `ZipkinSpanHandler` converting finished Brave
 * spans into `zipkin2.Span` objects handed to an application `Reporter<Span>`.
 *
 * An application that keeps recent traces in memory, for a debug page or for its own slow-request
 * alerting, plugs its own `Reporter<Span>` into Brave instead of a network sender. Each test builds
 * such a tracer, records spans the way instrumented request handling would, and reads back the
 * zipkin2 model the store received: kinds, endpoints, shared server joins, error tags, sampling and
 * debug decisions, and a filtering reporter in front of the store.
 */
public class BraveZipkinModelBridgeTest {

    @Test
    void clientAndServerSpansArriveAsZipkinModel() {
        TraceStore store = new TraceStore();
        SpanHandler handler = ZipkinSpanHandler.create(store);
        try (Tracing tracing = Tracing.newBuilder()
                .localServiceName("Checkout")
                .localIp("192.168.1.10")
                .localPort(8080)
                .traceId128Bit(true)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .addSpanHandler(handler)
                .build()) {
            Tracer tracer = tracing.tracer();

            brave.Span request = tracer.newTrace()
                    .kind(brave.Span.Kind.SERVER)
                    .name("GET /orders")
                    .tag("http.method", "GET")
                    .tag("http.path", "/orders");
            assertTrue(request.remoteIpAndPort("203.0.113.7", 51432));
            request.start(1_700_000_000_000_000L);

            brave.Span query = tracer.newChild(request.context())
                    .kind(brave.Span.Kind.CLIENT)
                    .name("SELECT orders")
                    .remoteServiceName("Postgres")
                    .tag("db.statement", "select * from orders where customer = ?");
            assertTrue(query.remoteIpAndPort("10.0.0.5", 5432));
            query.start(1_700_000_000_001_000L);
            query.annotate(1_700_000_000_001_200L, "ws");
            query.annotate(1_700_000_000_004_800L, "wr");
            query.finish(1_700_000_000_005_000L);

            request.finish(1_700_000_000_009_000L);

            List<Span> spans = store.spans();
            assertEquals(2, spans.size(), "children finish, and are reported, before their parent");

            Span db = spans.get(0);
            Span server = spans.get(1);

            assertEquals(32, server.traceId().length(), "traceId128Bit yields a 128-bit trace ID");
            assertEquals(request.context().traceIdString(), server.traceId());
            assertEquals(request.context().spanIdString(), server.id());
            assertNull(server.parentId(), "the request span is the trace root");
            assertEquals(Span.Kind.SERVER, server.kind());
            assertEquals("get /orders", server.name(), "zipkin2 lowercases span names");
            assertEquals(1_700_000_000_000_000L, server.timestampAsLong());
            assertEquals(9_000L, server.durationAsLong());
            assertEquals(Map.of("http.method", "GET", "http.path", "/orders"), server.tags());
            assertEquals(Endpoint.newBuilder()
                    .serviceName("checkout")
                    .ip("192.168.1.10")
                    .port(8080)
                    .build(), server.localEndpoint());
            assertNull(server.remoteEndpoint().serviceName());
            assertEquals("203.0.113.7", server.remoteEndpoint().ipv4());
            assertEquals(51432, server.remoteEndpoint().portAsInt());

            assertEquals(server.traceId(), db.traceId());
            assertEquals(server.id(), db.parentId());
            assertEquals(Span.Kind.CLIENT, db.kind());
            assertEquals("select orders", db.name());
            assertEquals(4_000L, db.durationAsLong());
            assertEquals("checkout", db.localServiceName());
            assertEquals("postgres", db.remoteServiceName());
            assertEquals("10.0.0.5", db.remoteEndpoint().ipv4());
            assertEquals(5432, db.remoteEndpoint().portAsInt());
            assertEquals(List.of(
                    Annotation.create(1_700_000_000_001_200L, "ws"),
                    Annotation.create(1_700_000_000_004_800L, "wr")), db.annotations());
            assertEquals("select * from orders where customer = ?", db.tags().get("db.statement"));
            assertNull(db.shared());
            assertNull(db.debug());
        }
    }

    @Test
    void joinedServerSpanIsSharedAndMessagingKindsAreKept() {
        TraceStore store = new TraceStore();
        SpanHandler handler = ZipkinSpanHandler.create(store);
        try (Tracing checkout = tracing("checkout", handler);
                Tracing inventory = Tracing.newBuilder()
                        .localServiceName("inventory")
                        .supportsJoin(true)
                        .addSpanHandler(handler)
                        .build()) {
            brave.Span reserve = checkout.tracer().newTrace()
                    .kind(brave.Span.Kind.CLIENT)
                    .name("reserve-stock");
            reserve.start(2_000_000L);

            // The inventory service receives the propagated IDs and joins the caller's span.
            TraceContext propagated = reserve.context();
            brave.Span handle = inventory.tracer().joinSpan(propagated)
                    .kind(brave.Span.Kind.SERVER)
                    .name("reserve-stock");
            handle.start(2_000_100L);
            handle.finish(2_000_900L);
            reserve.finish(2_001_000L);

            brave.Span publish = checkout.tracer().newChild(reserve.context())
                    .kind(brave.Span.Kind.PRODUCER)
                    .name("order-placed")
                    .remoteServiceName("kafka");
            publish.start(2_002_000L);
            publish.finish(2_002_050L);

            brave.Span consume = inventory.tracer().newChild(publish.context())
                    .kind(brave.Span.Kind.CONSUMER)
                    .name("order-placed")
                    .remoteServiceName("kafka");
            consume.start(2_003_000L);
            consume.finish(2_003_010L);

            List<Span> spans = store.spans();
            assertEquals(4, spans.size());

            Span server = spans.get(0);
            Span client = spans.get(1);
            assertEquals(client.id(), server.id(), "a joined server span reuses the client's ID");
            assertEquals(client.traceId(), server.traceId());
            assertEquals(Boolean.TRUE, server.shared());
            assertNull(client.shared());
            assertEquals(Span.Kind.SERVER, server.kind());
            assertEquals(Span.Kind.CLIENT, client.kind());
            assertEquals("inventory", server.localServiceName());
            assertEquals("checkout", client.localServiceName());

            Span producer = spans.get(2);
            Span consumer = spans.get(3);
            assertEquals(Span.Kind.PRODUCER, producer.kind());
            assertEquals(Span.Kind.CONSUMER, consumer.kind());
            assertEquals(producer.id(), consumer.parentId());
            assertEquals("kafka", producer.remoteServiceName());
            assertEquals("kafka", consumer.remoteServiceName());
            assertEquals(50L, producer.durationAsLong());
        }
    }

    @Test
    void errorsBecomeTagsThroughTheConfiguredErrorTag() {
        TraceStore defaults = new TraceStore();
        TraceStore typed = new TraceStore();
        SpanHandler defaultHandler = ZipkinSpanHandler.create(defaults);
        SpanHandler typedHandler = ZipkinSpanHandler.newBuilder(typed)
                .errorTag(new ExceptionTypeTag())
                .build();

        try (Tracing withDefault = tracing("payments", defaultHandler);
                Tracing withTyped = tracing("payments", typedHandler)) {
            recordCharge(withDefault.tracer(), new IllegalStateException("card declined"), null);
            recordCharge(withDefault.tracer(), new IllegalArgumentException(), null);
            recordCharge(withDefault.tracer(), new IllegalStateException("timeout"), "issuer-unavailable");

            recordCharge(withTyped.tracer(), new IllegalStateException("card declined"), null);
            recordCharge(withTyped.tracer(), new IllegalStateException("timeout"), "issuer-unavailable");
        }

        List<Span> byDefault = defaults.spans();
        assertEquals(3, byDefault.size());
        assertEquals("card declined", byDefault.get(0).tags().get("error"),
                "the default error tag carries the exception message");
        assertEquals("IllegalArgumentException", byDefault.get(1).tags().get("error"),
                "without a message the default error tag falls back to the type name");
        assertEquals("issuer-unavailable", byDefault.get(2).tags().get("error"),
                "an explicit error tag set by the application wins");

        List<Span> byType = typed.spans();
        assertEquals(2, byType.size());
        assertEquals("java.lang.IllegalStateException", byType.get(0).tags().get("exception.type"));
        assertFalse(byType.get(0).tags().containsKey("error"),
                "a custom error tag replaces the default one");
        assertEquals("issuer-unavailable", byType.get(1).tags().get("error"));
        assertFalse(byType.get(1).tags().containsKey("exception.type"),
                "no error tag is derived once the application tagged the error itself");
    }

    @Test
    void onlySampledSpansAreReportedUnlessAlwaysReportSpans() {
        TraceStore sampledOnly = new TraceStore();
        TraceStore everything = new TraceStore();
        SpanHandler sampledHandler = ZipkinSpanHandler.create(sampledOnly);
        SpanHandler everythingHandler = ZipkinSpanHandler.newBuilder(everything)
                .alwaysReportSpans(true)
                .build();

        // Sampling is off for export, but spans are still recorded for local handlers.
        try (Tracing tracing = Tracing.newBuilder()
                .localServiceName("search")
                .sampler(Sampler.NEVER_SAMPLE)
                .alwaysSampleLocal()
                .addSpanHandler(sampledHandler)
                .addSpanHandler(everythingHandler)
                .build()) {
            Tracer tracer = tracing.tracer();

            brave.Span unsampled = tracer.newTrace().name("query");
            assertEquals(Boolean.FALSE, unsampled.context().sampled());
            unsampled.start(3_000_000L);
            unsampled.finish(3_000_400L);

            brave.Span debug = tracer.nextSpan(TraceContextOrSamplingFlags.DEBUG).name("query-debug");
            assertTrue(debug.context().debug());
            debug.start(3_001_000L);
            debug.finish(3_001_300L);
        }

        List<Span> exported = sampledOnly.spans();
        assertEquals(1, exported.size(), "only the debug span is sampled");
        assertEquals("query-debug", exported.get(0).name());
        assertEquals(Boolean.TRUE, exported.get(0).debug());

        List<Span> all = everything.spans();
        assertEquals(2, all.size());
        assertEquals("query", all.get(0).name());
        assertNull(all.get(0).debug());
        assertEquals(400L, all.get(0).durationAsLong());
        assertEquals("query-debug", all.get(1).name());
    }

    @Test
    void filteringReporterAndHandlerIdentity() {
        TraceStore store = new TraceStore();
        HealthCheckFilter filter = new HealthCheckFilter(store, span -> "get /health".equals(span.name()));
        SpanHandler handler = ZipkinSpanHandler.create(filter);

        assertEquals(handler, ZipkinSpanHandler.create(filter), "handlers over one reporter are equal");
        assertEquals(handler.hashCode(), ZipkinSpanHandler.create(filter).hashCode());
        assertNotEquals(handler, ZipkinSpanHandler.create(store));
        assertEquals("HealthCheckFilter{store}", handler.toString(), "toString names the reporter");
        assertSame(SpanHandler.NOOP, ZipkinSpanHandler.create(Reporter.NOOP),
                "Reporter.NOOP switches the bridge off entirely");

        Tracing.Builder builder = Tracing.newBuilder()
                .localServiceName("gateway")
                .addSpanHandler(handler)
                .addSpanHandler(ZipkinSpanHandler.create(filter));
        assertEquals(1, builder.spanHandlers().size(), "an equal handler is only registered once");

        try (Tracing tracing = builder.build()) {
            Tracer tracer = tracing.tracer();
            for (String name : List.of("GET /health", "GET /orders", "GET /health", "POST /orders")) {
                brave.Span span = tracer.newTrace().kind(brave.Span.Kind.SERVER).name(name);
                span.start(4_000_000L);
                span.finish(4_000_010L);
            }
        }

        assertEquals(2, filter.dropped());
        List<String> names = new ArrayList<>();
        for (Span span : store.spans()) {
            names.add(span.name());
        }
        assertEquals(List.of("get /orders", "post /orders"), names);
    }

    private static Tracing tracing(String serviceName, SpanHandler handler) {
        return Tracing.newBuilder()
                .localServiceName(serviceName)
                .addSpanHandler(handler)
                .build();
    }

    private static void recordCharge(Tracer tracer, Throwable failure, String explicitError) {
        brave.Span charge = tracer.newTrace().kind(brave.Span.Kind.CLIENT).name("charge");
        charge.start(5_000_000L);
        if (explicitError != null) {
            charge.tag("error", explicitError);
        }
        charge.error(failure);
        charge.finish(5_000_100L);
    }

    /** The application's in-memory trace store, fed by the bridge in the finishing thread. */
    static final class TraceStore implements Reporter<Span> {

        private final List<Span> spans = new CopyOnWriteArrayList<>();

        @Override
        public void report(Span span) {
            spans.add(span);
        }

        List<Span> spans() {
            return List.copyOf(spans);
        }

        @Override
        public String toString() {
            return "store";
        }
    }

    /** Drops health-check spans before they reach the store, counting what it dropped. */
    static final class HealthCheckFilter implements Reporter<Span> {

        private final Reporter<Span> delegate;
        private final Predicate<Span> healthCheck;
        private int dropped;

        HealthCheckFilter(Reporter<Span> delegate, Predicate<Span> healthCheck) {
            this.delegate = delegate;
            this.healthCheck = healthCheck;
        }

        @Override
        public synchronized void report(Span span) {
            if (healthCheck.test(span)) {
                dropped++;
                return;
            }
            delegate.report(span);
        }

        synchronized int dropped() {
            return dropped;
        }

        @Override
        public String toString() {
            return "HealthCheckFilter{" + delegate + "}";
        }
    }

    /** Tags a failed span with the exception's class name instead of its message. */
    static final class ExceptionTypeTag extends Tag<Throwable> {

        ExceptionTypeTag() {
            super("exception.type");
        }

        @Override
        protected String parseValue(Throwable input, TraceContext context) {
            return input.getClass().getName();
        }
    }
}
