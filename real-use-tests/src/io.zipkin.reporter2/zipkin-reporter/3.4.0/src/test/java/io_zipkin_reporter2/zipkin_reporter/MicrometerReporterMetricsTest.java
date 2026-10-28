/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
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
import zipkin2.reporter.SpanBytesEncoder;
import zipkin2.reporter.metrics.micrometer.MicrometerReporterMetrics;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Reporter health metrics published to Micrometer through `MicrometerReporterMetrics`: the
 * `zipkin.reporter.*` message, span and drop counters, the queue gauges, the per-cause
 * `messages.dropped` counter, and extra tags that tell reporters sharing one registry apart.
 *
 * An inventory service exports its tracing health next to its other application metrics. Its
 * `AsyncReporter` posts JSON v2 batches over `URLConnectionSender` to a Zipkin collector played
 * by a JDK `HttpServer`, and every test reads the meters back from a `SimpleMeterRegistry`
 * exactly as a metrics scrape would see them.
 */
public class MicrometerReporterMetricsTest {

    private static final String TRACE_ID = "5b8aa5a2d2c872e8321cf37308d69df2";

    private HttpServer collector;
    private final BlockingQueue<byte[]> posts = new LinkedBlockingQueue<>();
    private final AtomicInteger responseStatus = new AtomicInteger(202);
    private String endpoint;

    @BeforeEach
    void startCollector() throws IOException {
        collector = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        collector.createContext("/api/v2/spans", this::receive);
        collector.start();
        endpoint = "http://127.0.0.1:" + collector.getAddress().getPort() + "/api/v2/spans";
    }

    @AfterEach
    void stopCollector() {
        collector.stop(0);
    }

    @Test
    void aDeliveredBatchIsCountedInTaggedMeters() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerReporterMetrics metrics = MicrometerReporterMetrics.builder(registry)
                .extraTags(Tag.of("application", "inventory"))
                .build();
        URLConnectionSender sender = uncompressedSender();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            List<Span> lookup = List.of(
                    span("0000000000000001", null, "get /items/{sku}"),
                    span("0000000000000002", "0000000000000001", "select-stock"),
                    span("0000000000000003", "0000000000000001", "read-cache"));
            lookup.forEach(reporter::report);
            reporter.flush();

            byte[] body = posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(body);
            assertEquals(lookup, SpanBytesDecoder.JSON_V2.decodeList(body));

            assertEquals(1.0, counter(registry, "zipkin.reporter.messages.total").count());
            assertEquals(body.length, counter(registry, "zipkin.reporter.messages").count());
            assertEquals(3.0, counter(registry, "zipkin.reporter.spans.total").count());
            assertEquals(encodedSize(lookup), counter(registry, "zipkin.reporter.spans").count());
            assertEquals(0.0, counter(registry, "zipkin.reporter.spans.dropped").count());
            assertEquals(0.0, gauge(registry, "zipkin.reporter.queue.spans").value());
            assertEquals(0.0, gauge(registry, "zipkin.reporter.queue.bytes").value());

            // Byte meters carry a base unit so dashboards label them; every meter has the tag.
            Meter.Id messageBytes = counter(registry, "zipkin.reporter.messages").getId();
            assertEquals("bytes", messageBytes.getBaseUnit());
            assertEquals("Total bytes of messages reported", messageBytes.getDescription());
            assertEquals("bytes", gauge(registry, "zipkin.reporter.queue.bytes").getId().getBaseUnit());
            for (Meter meter : registry.getMeters()) {
                assertEquals("inventory", meter.getId().getTag("application"), meter.getId().getName());
            }
            assertEquals(7, registry.getMeters().size());
        } finally {
            reporter.close();
            sender.close();
        }
        Reference.reachabilityFence(metrics);
    }

    @Test
    void queueGaugesTrackSpansLeftBehindBySizeLimitedFlushes() throws Exception {
        List<Span> restock = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            restock.add(span(String.format("%016x", 0x10 + i), null, "restock-shelf-" + i));
        }
        int spanSize = SpanBytesEncoder.JSON_V2.sizeInBytes(restock.get(0));
        // A JSON list of two spans needs the brackets and one comma on top of the span bytes.
        int twoSpanMessage = 2 * spanSize + 3;

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerReporterMetrics metrics = MicrometerReporterMetrics.create(registry);
        URLConnectionSender sender = uncompressedSender();
        // Only a byte-bounded queue tracks queued bytes, so the service sets a memory budget.
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .messageMaxBytes(twoSpanMessage)
                .queuedMaxBytes(1024 * 1024)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            restock.forEach(reporter::report);

            reporter.flush();
            assertEquals(restock.subList(0, 2), SpanBytesDecoder.JSON_V2.decodeList(nextPost()));
            assertEquals(2.0, gauge(registry, "zipkin.reporter.queue.spans").value());
            assertEquals(2.0 * spanSize, gauge(registry, "zipkin.reporter.queue.bytes").value());

            reporter.flush();
            assertEquals(restock.subList(2, 4), SpanBytesDecoder.JSON_V2.decodeList(nextPost()));
            assertEquals(0.0, gauge(registry, "zipkin.reporter.queue.spans").value());
            assertEquals(0.0, gauge(registry, "zipkin.reporter.queue.bytes").value());

            assertEquals(2.0, counter(registry, "zipkin.reporter.messages.total").count());
            assertEquals(4.0, counter(registry, "zipkin.reporter.spans.total").count());
        } finally {
            reporter.close();
            sender.close();
        }
        Reference.reachabilityFence(metrics);
    }

    @Test
    void aRejectedBatchIncrementsADropCounterTaggedWithItsCause() throws Exception {
        responseStatus.set(503);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerReporterMetrics metrics = MicrometerReporterMetrics.builder(registry)
                .extraTags(Tag.of("application", "inventory"))
                .build();
        URLConnectionSender sender = uncompressedSender();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            reporter.report(span("00000000000000a1", null, "post /reservations"));
            reporter.report(span("00000000000000a2", "00000000000000a1", "lock-sku"));
            reporter.flush();
            assertNotNull(nextPost());

            // The drop counter is created lazily, keyed by the simple name of the failure.
            Counter dropped = registry.get("zipkin.reporter.messages.dropped")
                    .tag("cause", "IOException")
                    .tag("application", "inventory")
                    .counter();
            assertEquals(1.0, dropped.count());
            assertEquals(1, registry.find("zipkin.reporter.messages.dropped").counters().size());
            assertEquals(1.0, counter(registry, "zipkin.reporter.messages.total").count());
            assertEquals(2.0, counter(registry, "zipkin.reporter.spans.dropped").count());
            assertEquals(0.0, gauge(registry, "zipkin.reporter.queue.spans").value());
        } finally {
            reporter.close();
            sender.close();
        }
        Reference.reachabilityFence(metrics);
    }

    @Test
    void reportersSharingARegistryAreToldApartByTheirExtraTags() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerReporterMetrics requestMetrics = MicrometerReporterMetrics.builder(registry)
                .extraTags(Tag.of("reporter", "requests"))
                .build();
        MicrometerReporterMetrics jobMetrics = MicrometerReporterMetrics.builder(registry)
                .extraTags(Tag.of("reporter", "jobs"))
                .build();
        URLConnectionSender sender = uncompressedSender();
        AsyncReporter<Span> requests = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .metrics(requestMetrics)
                .build(SpanBytesEncoder.JSON_V2);
        // The batch-job reporter keeps a tiny queue, so a burst of job spans overflows it.
        AsyncReporter<Span> jobs = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .queuedMaxSpans(1)
                .metrics(jobMetrics)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            requests.report(span("00000000000000b1", null, "get /items"));
            requests.report(span("00000000000000b2", null, "put /items/{sku}"));
            for (int i = 1; i <= 4; i++) {
                jobs.report(span(String.format("%016x", 0xc0 + i), null, "nightly-reindex-" + i));
            }

            requests.flush();
            assertEquals(2, SpanBytesDecoder.JSON_V2.decodeList(nextPost()).size());
            jobs.flush();
            assertEquals(1, SpanBytesDecoder.JSON_V2.decodeList(nextPost()).size());

            assertEquals(2.0, taggedCounter(registry, "zipkin.reporter.spans.total", "requests").count());
            assertEquals(0.0, taggedCounter(registry, "zipkin.reporter.spans.dropped", "requests").count());
            assertEquals(4.0, taggedCounter(registry, "zipkin.reporter.spans.total", "jobs").count());
            assertEquals(3.0, taggedCounter(registry, "zipkin.reporter.spans.dropped", "jobs").count());
            assertEquals(1.0, taggedCounter(registry, "zipkin.reporter.messages.total", "jobs").count());
            assertEquals(2, registry.find("zipkin.reporter.queue.spans").gauges().size());
            assertTrue(posts.isEmpty());
        } finally {
            requests.close();
            jobs.close();
            sender.close();
        }
        Reference.reachabilityFence(requestMetrics);
        Reference.reachabilityFence(jobMetrics);
    }

    private URLConnectionSender uncompressedSender() {
        return URLConnectionSender.newBuilder().endpoint(endpoint).compressionEnabled(false).build();
    }

    private byte[] nextPost() throws InterruptedException {
        byte[] body = posts.poll(10, TimeUnit.SECONDS);
        assertNotNull(body);
        return body;
    }

    private void receive(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            posts.add(in.readAllBytes());
        }
        exchange.sendResponseHeaders(responseStatus.get(), -1);
        exchange.close();
    }

    private static Counter counter(SimpleMeterRegistry registry, String name) {
        return registry.get(name).counter();
    }

    private static Counter taggedCounter(SimpleMeterRegistry registry, String name, String reporter) {
        return registry.get(name).tag("reporter", reporter).counter();
    }

    private static Gauge gauge(SimpleMeterRegistry registry, String name) {
        return registry.get(name).gauge();
    }

    private static double encodedSize(List<Span> spans) {
        long size = 0;
        for (Span span : spans) {
            size += SpanBytesEncoder.JSON_V2.sizeInBytes(span);
        }
        return size;
    }

    private static Span span(String id, String parentId, String name) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(parentId)
                .id(id)
                .name(name)
                .kind(Span.Kind.SERVER)
                .localEndpoint(Endpoint.newBuilder().serviceName("inventory").ip("10.0.0.12").build())
                .timestamp(1_700_000_000_000_000L)
                .duration(2_400L)
                .putTag("http.route", "/items")
                .build();
    }
}
