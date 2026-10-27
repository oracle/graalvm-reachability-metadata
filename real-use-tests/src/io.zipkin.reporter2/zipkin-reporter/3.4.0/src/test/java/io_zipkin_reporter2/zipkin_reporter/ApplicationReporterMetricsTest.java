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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.SpanBytesEncoder;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * The `ReporterMetrics` callback contract as an application implements it: an own `ReporterMetrics`
 * sink that `AsyncReporter` feeds with span, byte, message, drop and queue updates from concurrent
 * request threads, a tracing health check derived from those callbacks, and a fan-out that keeps
 * the sink and the library's `InMemoryReporterMetrics` in agreement through close.
 *
 * An order service has its own operations dashboard and health endpoint instead of a metrics
 * library, so it implements `ReporterMetrics` as a `TracingLedger` of plain counters. Its
 * `AsyncReporter` posts JSON v2 batches over `URLConnectionSender` to a Zipkin collector played by
 * a JDK `HttpServer`; the service flushes explicitly from its own scheduler, which every test does
 * by calling `flush()`, so the ledger is read back at well-defined points.
 */
public class ApplicationReporterMetricsTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

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
    void concurrentRequestThreadsOverflowTheQueueAndTheLedgerBalances() throws Exception {
        int threads = 4;
        int spansPerThread = 50;
        int queueCapacity = 120;
        int spanSize = SpanBytesEncoder.JSON_V2.sizeInBytes(span(1, 1));
        // Every span encodes to the same size, so a message holds exactly fifty of them.
        int fiftySpanMessage = Encoding.JSON.listSizeInBytes(50 * spanSize) + 49;

        TracingLedger ledger = new TracingLedger();
        URLConnectionSender sender = uncompressedSender();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .messageMaxBytes(fiftySpanMessage)
                .queuedMaxSpans(queueCapacity)
                .metrics(ledger)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            for (int worker = 1; worker <= threads; worker++) {
                int workerId = worker;
                Thread thread = new Thread(() -> {
                    try {
                        gate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int request = 1; request <= spansPerThread; request++) {
                        reporter.report(span(workerId, request));
                    }
                }, "order-request-" + worker);
                thread.start();
                workers.add(thread);
            }
            gate.countDown();
            for (Thread thread : workers) {
                thread.join(TimeUnit.SECONDS.toMillis(30));
                assertFalse(thread.isAlive(), thread.getName());
            }

            // Every request thread was counted; whatever did not fit the queue is dropped on report.
            assertEquals(threads * spansPerThread, ledger.spans.get());
            assertEquals(threads * spansPerThread - queueCapacity, ledger.spansDropped.get());
            assertEquals(0, ledger.messages.get());

            List<Integer> batchSizes = new ArrayList<>();
            List<Long> queuedAfterFlush = new ArrayList<>();
            Set<String> deliveredIds = new HashSet<>();
            long postedBytes = 0;
            long deliveredSpanBytes = 0;
            for (int flush = 0; flush < 3; flush++) {
                reporter.flush();
                byte[] body = nextPost();
                postedBytes += body.length;
                List<Span> batch = SpanBytesDecoder.JSON_V2.decodeList(body);
                batchSizes.add(batch.size());
                for (Span delivered : batch) {
                    assertTrue(deliveredIds.add(delivered.id()), delivered.id());
                    deliveredSpanBytes += SpanBytesEncoder.JSON_V2.sizeInBytes(delivered);
                }
                queuedAfterFlush.add(ledger.queuedSpans.get());
            }

            assertEquals(List.of(50, 50, 20), batchSizes);
            assertEquals(List.of(70L, 20L, 0L), queuedAfterFlush);
            assertEquals(queueCapacity, deliveredIds.size());
            assertEquals(3, ledger.messages.get());
            assertEquals(postedBytes, ledger.messageBytes.get());
            assertEquals((long) queueCapacity * spanSize, deliveredSpanBytes);
            // Span bytes are counted per drained span, so the span that overflowed each of the two
            // full batches is counted again when the next flush takes it.
            assertEquals(deliveredSpanBytes + 2L * spanSize, ledger.spanBytes.get());
            assertEquals(0, ledger.messagesDropped.get());
            assertTrue(ledger.dropsByCause.isEmpty());
            assertTrue(posts.isEmpty());
        } finally {
            reporter.close();
            sender.close();
        }
    }

    @Test
    void aCollectorOutageTurnsTheHealthCheckDownUntilDeliveryResumes() throws Exception {
        TracingLedger ledger = new TracingLedger();
        TracingHealth health = new TracingHealth(ledger);
        URLConnectionSender sender = uncompressedSender();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .metrics(ledger)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            assertEquals("UNKNOWN", health.check().status());

            reporter.report(span(1, 1));
            reporter.flush();
            nextPost();
            HealthReport healthy = health.check();
            assertEquals("UP", healthy.status());
            assertEquals(1, healthy.deliveredMessages());
            assertNull(healthy.lastFailure());

            // The collector is being redeployed: the next batch is rejected and its spans are lost.
            responseStatus.set(503);
            reporter.report(span(1, 2));
            reporter.report(span(1, 3));
            reporter.flush();
            nextPost();
            HealthReport outage = health.check();
            assertEquals("DOWN", outage.status());
            assertEquals(1, outage.deliveredMessages());
            assertEquals(2, outage.spansDropped());
            assertEquals("IOException", outage.lastFailure());
            assertEquals(Map.of("IOException", 1L), Map.copyOf(ledger.dropsByCause));

            responseStatus.set(202);
            reporter.report(span(1, 4));
            reporter.flush();
            assertEquals(List.of(span(1, 4)), SpanBytesDecoder.JSON_V2.decodeList(nextPost()));
            HealthReport recovered = health.check();
            assertEquals("UP", recovered.status());
            assertEquals(2, recovered.deliveredMessages());
            // The outage stays on the dashboard after delivery resumes.
            assertEquals(2, recovered.spansDropped());
            assertEquals("IOException", recovered.lastFailure());
            assertEquals(3, ledger.messages.get());
            assertEquals(1, ledger.messagesDropped.get());
        } finally {
            reporter.close();
            sender.close();
        }
    }

    @Test
    void aFanOutKeepsTheLedgerAndInMemoryMetricsInAgreementThroughClose() throws Exception {
        TracingLedger ledger = new TracingLedger();
        InMemoryReporterMetrics inMemory = new InMemoryReporterMetrics();
        ReporterMetrics metrics = new FanOutMetrics(ledger, inMemory);
        URLConnectionSender sender = uncompressedSender();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .queuedMaxSpans(4)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);

        for (int request = 1; request <= 5; request++) {
            reporter.report(span(2, request));
        }
        reporter.flush();
        byte[] body = nextPost();
        assertEquals(4, SpanBytesDecoder.JSON_V2.decodeList(body).size());
        assertEquals(body.length, inMemory.messageBytes());

        // Spans still queued at shutdown and spans reported afterwards are counted as dropped.
        reporter.report(span(2, 6));
        reporter.report(span(2, 7));
        reporter.close();
        reporter.report(span(2, 8));
        assertThrows(ClosedSenderException.class, reporter::flush);
        sender.close();

        assertEquals(8, inMemory.spans());
        assertEquals(1 + 2 + 1, inMemory.spansDropped());
        assertEquals(1, inMemory.messages());
        assertEquals(0, inMemory.messagesDropped());
        assertEquals(0, inMemory.queuedSpans());

        assertEquals(inMemory.spans(), ledger.spans.get());
        assertEquals(inMemory.spansDropped(), ledger.spansDropped.get());
        assertEquals(inMemory.messages(), ledger.messages.get());
        assertEquals(inMemory.messageBytes(), ledger.messageBytes.get());
        assertEquals(inMemory.spanBytes(), ledger.spanBytes.get());
        assertEquals(inMemory.queuedSpans(), ledger.queuedSpans.get());
        assertTrue(posts.isEmpty());
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

    /// One span per handled request; fixed-width ids and names keep every encoded span the same size.
    private static Span span(int worker, int request) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .id(String.format("%08x%08x", worker, request))
                .name(String.format("post /orders/%02d", worker))
                .kind(Span.Kind.SERVER)
                .localEndpoint(Endpoint.newBuilder().serviceName("orders").ip("10.0.0.21").build())
                .timestamp(1_700_000_000_000_000L + request)
                .duration(3_100L)
                .putTag("http.route", "/orders")
                .build();
    }

    /// The service's own `ReporterMetrics`: thread-safe counters its dashboard and health check read.
    static final class TracingLedger implements ReporterMetrics {
        final AtomicLong messages = new AtomicLong();
        final AtomicLong messagesDropped = new AtomicLong();
        final AtomicLong messageBytes = new AtomicLong();
        final AtomicLong spans = new AtomicLong();
        final AtomicLong spanBytes = new AtomicLong();
        final AtomicLong spansDropped = new AtomicLong();
        final AtomicLong queuedSpans = new AtomicLong();
        final AtomicLong queuedBytes = new AtomicLong();
        final Map<String, Long> dropsByCause = new ConcurrentHashMap<>();
        final AtomicReference<String> lastFailure = new AtomicReference<>();
        final AtomicReference<Boolean> lastMessageFailed = new AtomicReference<>();

        @Override
        public void incrementMessages() {
            messages.incrementAndGet();
            // The reporter signals a message before sending it and reports a drop only on failure.
            lastMessageFailed.set(false);
        }

        @Override
        public void incrementMessagesDropped(Throwable cause) {
            messagesDropped.incrementAndGet();
            String name = cause.getClass().getSimpleName();
            dropsByCause.merge(name, 1L, Long::sum);
            lastFailure.set(name);
            lastMessageFailed.set(true);
        }

        @Override
        public void incrementSpans(int quantity) {
            spans.addAndGet(quantity);
        }

        @Override
        public void incrementSpanBytes(int quantity) {
            spanBytes.addAndGet(quantity);
        }

        @Override
        public void incrementMessageBytes(int quantity) {
            messageBytes.addAndGet(quantity);
        }

        @Override
        public void incrementSpansDropped(int quantity) {
            spansDropped.addAndGet(quantity);
        }

        @Override
        public void updateQueuedSpans(int update) {
            queuedSpans.set(update);
        }

        @Override
        public void updateQueuedBytes(int update) {
            queuedBytes.set(update);
        }
    }

    /// What the service's health endpoint returns for its tracing pipeline.
    record HealthReport(String status, long deliveredMessages, long spansDropped, String lastFailure) {
    }

    /// Derives the tracing health of the service from its ledger.
    static final class TracingHealth {
        private final TracingLedger ledger;

        TracingHealth(TracingLedger ledger) {
            this.ledger = ledger;
        }

        HealthReport check() {
            Boolean failed = ledger.lastMessageFailed.get();
            String status = failed == null ? "UNKNOWN" : failed ? "DOWN" : "UP";
            return new HealthReport(status, ledger.messages.get() - ledger.messagesDropped.get(),
                    ledger.spansDropped.get(), ledger.lastFailure.get());
        }
    }

    /// Forwards every callback to each delegate, so one reporter feeds two metrics backends.
    static final class FanOutMetrics implements ReporterMetrics {
        private final List<ReporterMetrics> delegates;

        FanOutMetrics(ReporterMetrics... delegates) {
            this.delegates = List.of(delegates);
        }

        @Override
        public void incrementMessages() {
            delegates.forEach(ReporterMetrics::incrementMessages);
        }

        @Override
        public void incrementMessagesDropped(Throwable cause) {
            delegates.forEach(metrics -> metrics.incrementMessagesDropped(cause));
        }

        @Override
        public void incrementSpans(int quantity) {
            delegates.forEach(metrics -> metrics.incrementSpans(quantity));
        }

        @Override
        public void incrementSpanBytes(int quantity) {
            delegates.forEach(metrics -> metrics.incrementSpanBytes(quantity));
        }

        @Override
        public void incrementMessageBytes(int quantity) {
            delegates.forEach(metrics -> metrics.incrementMessageBytes(quantity));
        }

        @Override
        public void incrementSpansDropped(int quantity) {
            delegates.forEach(metrics -> metrics.incrementSpansDropped(quantity));
        }

        @Override
        public void updateQueuedSpans(int update) {
            delegates.forEach(metrics -> metrics.updateQueuedSpans(update));
        }

        @Override
        public void updateQueuedBytes(int update) {
            delegates.forEach(metrics -> metrics.updateQueuedBytes(update));
        }
    }
}
