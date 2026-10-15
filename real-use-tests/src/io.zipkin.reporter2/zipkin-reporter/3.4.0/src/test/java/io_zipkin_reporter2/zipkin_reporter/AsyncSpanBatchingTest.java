/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batches the spans of a traced request through an `AsyncReporter` into JSON messages delivered
 * by an application-defined transport.
 *
 * The application records a server span and the client spans it made downstream, and the
 * reporter delivers them as JSON v2 lists that a collector decodes back into the same spans.
 * One variant flushes on demand, the other lets the reporter's own flush thread deliver the
 * batch once the message timeout elapses.
 */
public class AsyncSpanBatchingTest {

    private static final String TRACE_ID = "86154a4ba6e91385";
    private static final Endpoint CHECKOUT = Endpoint.newBuilder()
            .serviceName("checkout")
            .ip("10.0.0.7")
            .port(8080)
            .build();

    @Test
    void flushDeliversRecordedRequestSpansAsOneJsonMessage() throws Exception {
        CollectorTransport transport = new CollectorTransport(500_000);
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        List<Span> requestSpans = requestSpans();

        try (AsyncReporter<Span> reporter = AsyncReporter.builder(transport)
                .metrics(metrics)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build()) {
            requestSpans.forEach(reporter::report);
            assertTrue(transport.messages.isEmpty(), "spans are buffered until the flush");

            reporter.flush();
        }

        byte[] message = transport.messages.poll(10, TimeUnit.SECONDS);
        assertNotNull(message, "message delivered by the flush");
        assertTrue(transport.messages.isEmpty(), "one flush sends one message");
        assertEquals(requestSpans, SpanBytesDecoder.JSON_V2.decodeList(message));
        assertEquals(requestSpans.size(), metrics.spans());
        assertEquals(1, metrics.messages());
        assertEquals(0, metrics.spansDropped());
        assertEquals(0, metrics.queuedSpans());
    }

    @Test
    void flushThreadDeliversSpansAfterMessageTimeout() throws Exception {
        CollectorTransport transport = new CollectorTransport(500_000);
        List<Span> requestSpans = requestSpans();

        try (AsyncReporter<Span> reporter = AsyncReporter.builder(transport)
                .messageTimeout(200, TimeUnit.MILLISECONDS)
                .build()) {
            requestSpans.forEach(reporter::report);

            List<Span> collected = new ArrayList<>();
            while (collected.size() < requestSpans.size()) {
                byte[] message = transport.messages.poll(30, TimeUnit.SECONDS);
                assertNotNull(message, "message from the reporter flush thread");
                collected.addAll(SpanBytesDecoder.JSON_V2.decodeList(message));
            }
            assertEquals(requestSpans, collected);
        }
    }

    private static List<Span> requestSpans() {
        long start = 1_700_000_000_000_000L;
        Span server = Span.newBuilder()
                .traceId(TRACE_ID)
                .id(TRACE_ID)
                .kind(Span.Kind.SERVER)
                .name("post /orders")
                .localEndpoint(CHECKOUT)
                .timestamp(start)
                .duration(48_000L)
                .putTag("http.method", "POST")
                .putTag("http.path", "/orders")
                .build();
        Span inventory = clientSpan("a2fb4a1d1a96d312", "inventory", "get /stock", start + 2_000L);
        Span payments = clientSpan("b7ad6b7169203331", "payments", "post /charges", start + 20_000L);
        return List.of(server, inventory, payments);
    }

    private static Span clientSpan(String id, String remoteService, String name, long timestamp) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(TRACE_ID)
                .id(id)
                .kind(Span.Kind.CLIENT)
                .name(name)
                .localEndpoint(CHECKOUT)
                .remoteEndpoint(Endpoint.newBuilder().serviceName(remoteService).build())
                .timestamp(timestamp)
                .duration(12_000L)
                .addAnnotation(timestamp + 1_000L, "wire.send")
                .build();
    }

    /**
     * Transport that hands each JSON message to an in-process collector queue, the way an
     * application plugs its own delivery mechanism into the reporter.
     */
    static final class CollectorTransport implements BytesMessageSender {

        final BlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();
        private final int messageMaxBytes;

        CollectorTransport(int messageMaxBytes) {
            this.messageMaxBytes = messageMaxBytes;
        }

        @Override
        public Encoding encoding() {
            return Encoding.JSON;
        }

        @Override
        public int messageMaxBytes() {
            return messageMaxBytes;
        }

        @Override
        public int messageSizeInBytes(List<byte[]> encodedSpans) {
            return encoding().listSizeInBytes(encodedSpans);
        }

        @Override
        public int messageSizeInBytes(int encodedSizeInBytes) {
            return encoding().listSizeInBytes(encodedSizeInBytes);
        }

        @Override
        public void send(List<byte[]> encodedSpans) {
            messages.add(encoding().encode(encodedSpans));
        }

        @Override
        public void close() {
        }
    }
}
