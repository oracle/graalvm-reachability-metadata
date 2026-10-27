/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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
import zipkin2.reporter.Reporter;
import zipkin2.reporter.SpanBytesEncoder;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * The `AsyncReporter` lifecycle and its memory budgets: the lazily started flusher thread from an
 * application `ThreadFactory`, background delivery on the message timeout, spans dropped for
 * exceeding the message or queued-bytes budget, and the graceful-shutdown order of
 * `flush()` then `close()` with everything left unflushed or reported afterwards counted as dropped.
 *
 * A checkout service hands its finished spans to an `AsyncReporter` behind the plain `Reporter`
 * interface its request handlers see. The reporter posts to a Zipkin collector played by a JDK
 * `HttpServer` through `URLConnectionSender`, and the service watches `InMemoryReporterMetrics`
 * to see what tracing cost it and what it lost.
 */
public class AsyncReporterLifecycleTest {

    private static final String TRACE_ID = "4e441824ec2b6a44ffdc9bb9a6453df3";

    private HttpServer collector;
    private final BlockingQueue<byte[]> posts = new LinkedBlockingQueue<>();
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
    void theFlusherThreadStartsOnTheFirstReportAndDeliversWithoutAnExplicitFlush() throws Exception {
        NamedFlusherThreads threads = new NamedFlusherThreads("checkout-tracing");
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        URLConnectionSender sender = URLConnectionSender.newBuilder()
                .endpoint(endpoint)
                .encoding(Encoding.THRIFT)
                .compressionEnabled(false)
                .build();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .threadFactory(threads)
                .messageTimeout(100, TimeUnit.MILLISECONDS)
                .metrics(metrics)
                .build(SpanBytesEncoder.THRIFT);
        try {
            // A reporter nobody reports to never costs the service a thread.
            assertTrue(threads.created.isEmpty());

            Reporter<Span> handlerView = reporter;
            List<Span> checkout = List.of(
                    span("0000000000000001", null, "post /checkout"),
                    span("0000000000000002", "0000000000000001", "charge-card"),
                    span("0000000000000003", "0000000000000001", "reserve-stock"));
            checkout.forEach(handlerView::report);

            assertEquals(1, threads.created.size());
            Thread flusher = threads.created.get(0);
            // The reporter renames the thread after itself, so a thread dump names the collector.
            assertEquals(reporter.toString(), flusher.getName());
            assertEquals("AsyncReporter{URLConnectionSender{" + endpoint + "}}", flusher.getName());
            assertTrue(flusher.isDaemon());

            // The timeout may split the burst across posts, so collect until every span arrived.
            Set<Span> delivered = new HashSet<>();
            while (delivered.size() < checkout.size()) {
                byte[] body = posts.poll(10, TimeUnit.SECONDS);
                assertNotNull(body, "collector received " + delivered);
                delivered.addAll(SpanBytesDecoder.THRIFT.decodeList(body));
            }
            assertEquals(new HashSet<>(checkout), delivered);
            assertEquals(3, metrics.spans());
            assertEquals(0, metrics.spansDropped());
            assertTrue(metrics.messages() >= 1);
        } finally {
            reporter.close();
            sender.close();
        }
        assertEquals(1, threads.created.size());
    }

    @Test
    void aSpanTooLargeForOneMessageIsDroppedOnFlush() throws Exception {
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        URLConnectionSender sender = uncompressedSender();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .messageMaxBytes(2048)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            // A handler that attaches a whole cart dump as a tag produces an unsendable span.
            Span cartDump = span("00000000000000a1", null, "post /checkout").toBuilder()
                    .putTag("cart.json", "x".repeat(4096))
                    .build();
            reporter.report(cartDump);
            assertEquals(1, metrics.spans());
            assertEquals(0, metrics.spansDropped());

            // The flush that meets it drops it, and the collector receives an empty span list.
            reporter.flush();
            assertEquals(List.of(), SpanBytesDecoder.JSON_V2.decodeList(nextPost()));
            assertEquals(1, metrics.spansDropped());
            assertEquals(0, metrics.queuedSpans());

            // The reporter keeps working for spans that fit.
            Span ordinary = span("00000000000000a2", null, "get /cart");
            reporter.report(ordinary);
            reporter.flush();
            assertEquals(List.of(ordinary), SpanBytesDecoder.JSON_V2.decodeList(nextPost()));
            assertEquals(2, metrics.messages());
            assertEquals(2, metrics.spans());
            assertEquals(1, metrics.spansDropped());
        } finally {
            reporter.close();
            sender.close();
        }
    }

    @Test
    void theQueuedBytesBudgetRejectsSpansThatDoNotFit() throws Exception {
        List<Span> burst = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            burst.add(span(String.format("%016x", 0xb0 + i), null, "price-line-" + i));
        }
        int spanSize = SpanBytesEncoder.JSON_V2.sizeInBytes(burst.get(0));

        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        URLConnectionSender sender = uncompressedSender();
        // The service caps tracing memory at room for two and a half spans.
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .queuedMaxBytes(spanSize * 5 / 2)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            burst.forEach(reporter::report);
            assertEquals(4, metrics.spans());
            assertEquals(2, metrics.spansDropped());
            assertEquals(4L * spanSize, metrics.spanBytes());

            reporter.flush();
            assertEquals(burst.subList(0, 2), SpanBytesDecoder.JSON_V2.decodeList(nextPost()));
            assertEquals(0, metrics.queuedSpans());
            assertEquals(0, metrics.queuedBytes());

            // Draining the queue frees the budget for the next burst.
            reporter.report(burst.get(2));
            reporter.flush();
            assertEquals(List.of(burst.get(2)), SpanBytesDecoder.JSON_V2.decodeList(nextPost()));
            assertEquals(2, metrics.spansDropped());
        } finally {
            reporter.close();
            sender.close();
        }
    }

    @Test
    void gracefulShutdownFlushesFirstAndCountsEverythingElseAsDropped() throws Exception {
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        URLConnectionSender sender = uncompressedSender();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .closeTimeout(2, TimeUnit.SECONDS)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);

        List<Span> lastRequests = List.of(
                span("00000000000000c1", null, "post /checkout"),
                span("00000000000000c2", "00000000000000c1", "send-receipt"));
        lastRequests.forEach(reporter::report);
        reporter.flush();
        assertEquals(lastRequests, SpanBytesDecoder.JSON_V2.decodeList(nextPost()));

        // A span finished after the shutdown hook flushed is still queued when close runs.
        reporter.report(span("00000000000000c3", null, "get /health"));
        reporter.close();
        assertEquals(1, metrics.spansDropped());
        assertEquals(0, metrics.queuedSpans());
        assertTrue(posts.isEmpty());

        // Late reports are dropped, a second close is harmless, and flushing is refused.
        reporter.report(span("00000000000000c4", null, "get /health"));
        assertEquals(2, metrics.spansDropped());
        reporter.close();
        assertThrows(ClosedSenderException.class, reporter::flush);
        assertEquals(1, metrics.messages());

        // Closing the reporter leaves the sender to its owner, which can still post directly.
        Span direct = span("00000000000000c5", null, "post /refund");
        sender.send(List.of(SpanBytesEncoder.JSON_V2.encode(direct)));
        assertEquals(List.of(direct), SpanBytesDecoder.JSON_V2.decodeList(nextPost()));
        sender.close();
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
        exchange.sendResponseHeaders(202, -1);
        exchange.close();
    }

    private static Span span(String id, String parentId, String name) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(parentId)
                .id(id)
                .name(name)
                .kind(Span.Kind.SERVER)
                .localEndpoint(Endpoint.newBuilder().serviceName("checkout").ip("10.0.4.7").build())
                .timestamp(1_700_000_000_000_000L)
                .duration(3_100L)
                .putTag("http.method", "POST")
                .build();
    }

    /// The service's thread factory: keeps the flusher from blocking JVM exit and records it.
    private static final class NamedFlusherThreads implements ThreadFactory {

        private final String name;
        private final List<Thread> created = new CopyOnWriteArrayList<>();

        NamedFlusherThreads(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            created.add(thread);
            return thread;
        }
    }
}
