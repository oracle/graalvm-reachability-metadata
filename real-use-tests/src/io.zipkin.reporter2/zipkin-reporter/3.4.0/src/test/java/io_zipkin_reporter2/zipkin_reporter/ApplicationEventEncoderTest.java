/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.BytesEncoder;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reports an application-defined event type through an `AsyncReporter` built with a custom
 * `BytesEncoder`, bounded by the reporter's queue and message size limits.
 *
 * The application does not use the Zipkin span model: it encodes its own audit events as JSON
 * objects, and the reporter batches them into JSON list messages for its transport. The
 * scenarios cover splitting a backlog into size-bounded messages, dropping events that overflow
 * the queue or exceed the message size, delivery on a flush thread from the application's
 * thread factory, and rejecting an encoder whose encoding the transport does not speak.
 */
public class ApplicationEventEncoderTest {

    private static final int MESSAGE_MAX_BYTES = 200;
    private static final Pattern EVENT = Pattern.compile(
            "\\{\"user\":\"([^\"]*)\",\"action\":\"([^\"]*)\",\"at\":(\\d+)}");

    @Test
    void flushSplitsBacklogIntoSizeBoundedJsonMessages() throws Exception {
        EventTransport transport = new EventTransport(Encoding.JSON);
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        List<AuditEvent> events = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            events.add(new AuditEvent("user-" + i, i % 2 == 0 ? "login" : "logout", 1_000L + i));
        }

        List<AuditEvent> collected = new ArrayList<>();
        int messageCount = 0;
        try (AsyncReporter<AuditEvent> reporter = AsyncReporter.builder(transport)
                .metrics(metrics)
                .messageMaxBytes(MESSAGE_MAX_BYTES)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build(new AuditEventEncoder(Encoding.JSON))) {
            events.forEach(reporter::report);

            while (collected.size() < events.size()) {
                assertTrue(messageCount < events.size(), "every flush delivers at least one event");
                reporter.flush();
                byte[] message = transport.messages.poll(10, TimeUnit.SECONDS);
                assertNotNull(message, "message delivered by the flush");
                assertTrue(message.length <= MESSAGE_MAX_BYTES,
                        "message of " + message.length + " bytes fits the configured limit");
                collected.addAll(decode(message));
                messageCount++;
            }
        }

        assertEquals(events, collected);
        assertTrue(messageCount > 1, "the backlog does not fit one message");
        assertEquals(messageCount, metrics.messages());
        assertEquals(events.size(), metrics.spans());
        assertEquals(0, metrics.spansDropped());
    }

    @Test
    void reporterDropsEventsBeyondQueueCapacityAndMessageSize() throws Exception {
        EventTransport transport = new EventTransport(Encoding.JSON);
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        AuditEvent oversized = new AuditEvent("bulk", "x".repeat(MESSAGE_MAX_BYTES), 1L);
        List<AuditEvent> accepted = List.of(
                new AuditEvent("alice", "login", 10L),
                new AuditEvent("bob", "login", 11L),
                new AuditEvent("carol", "export", 12L));

        try (AsyncReporter<AuditEvent> reporter = AsyncReporter.builder(transport)
                .metrics(metrics)
                .messageMaxBytes(MESSAGE_MAX_BYTES)
                .queuedMaxSpans(accepted.size())
                .queuedMaxBytes(64 * 1024)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build(new AuditEventEncoder(Encoding.JSON))) {
            reporter.report(oversized);
            assertEquals(1, metrics.spansDropped(), "an event larger than a message is dropped");

            accepted.forEach(reporter::report);
            reporter.report(new AuditEvent("dave", "delete", 13L));
            reporter.report(new AuditEvent("erin", "delete", 14L));
            assertEquals(3, metrics.spansDropped(), "events beyond the queue capacity are dropped");

            reporter.flush();
        }

        byte[] message = transport.messages.poll(10, TimeUnit.SECONDS);
        assertNotNull(message, "queued events delivered by the flush");
        assertEquals(accepted, decode(message));
        assertTrue(transport.messages.isEmpty(), "dropped events are never delivered");
        assertEquals(6, metrics.spans());
        assertEquals(1, metrics.messages());
    }

    @Test
    void flushThreadFromApplicationFactoryDeliversEvents() throws Exception {
        EventTransport transport = new EventTransport(Encoding.JSON);
        List<Thread> createdThreads = new CopyOnWriteArrayList<>();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            createdThreads.add(thread);
            return thread;
        };
        AuditEvent event = new AuditEvent("frank", "password-reset", 42L);

        try (AsyncReporter<AuditEvent> reporter = AsyncReporter.builder(transport)
                .threadFactory(threadFactory)
                .messageTimeout(100, TimeUnit.MILLISECONDS)
                .build(new AuditEventEncoder(Encoding.JSON))) {
            reporter.report(event);

            byte[] message = transport.messages.poll(30, TimeUnit.SECONDS);
            assertNotNull(message, "message from the application's flush thread");
            assertEquals(List.of(event), decode(message));
            assertEquals(1, createdThreads.size(), "the reporter starts one flush thread");
            assertSame(createdThreads.get(0), transport.senderThreads.poll());
        }
    }

    @Test
    void encoderMustMatchTransportEncoding() {
        EventTransport transport = new EventTransport(Encoding.JSON);
        AsyncReporter.Builder builder = AsyncReporter.builder(transport);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> builder.build(new AuditEventEncoder(Encoding.PROTO3)));
        assertTrue(error.getMessage().contains("PROTO3"), error.getMessage());
    }

    private static List<AuditEvent> decode(byte[] message) {
        String json = new String(message, StandardCharsets.UTF_8);
        assertTrue(json.startsWith("[") && json.endsWith("]"), json);
        List<AuditEvent> events = new ArrayList<>();
        Matcher matcher = EVENT.matcher(json);
        while (matcher.find()) {
            events.add(new AuditEvent(matcher.group(1), matcher.group(2),
                    Long.parseLong(matcher.group(3))));
        }
        return events;
    }

    /** Audit event the application records instead of a Zipkin span. */
    record AuditEvent(String user, String action, long at) {
    }

    /** Encodes an audit event as one JSON object, the element of a JSON list message. */
    static final class AuditEventEncoder implements BytesEncoder<AuditEvent> {

        private final Encoding encoding;

        AuditEventEncoder(Encoding encoding) {
            this.encoding = encoding;
        }

        @Override
        public Encoding encoding() {
            return encoding;
        }

        @Override
        public int sizeInBytes(AuditEvent event) {
            return encode(event).length;
        }

        @Override
        public byte[] encode(AuditEvent event) {
            String json = """
                    {"user":"%s","action":"%s","at":%d}"""
                    .formatted(event.user(), event.action(), event.at());
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Transport that hands each list message to an in-process event store. */
    static final class EventTransport implements BytesMessageSender {

        final BlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<Thread> senderThreads = new LinkedBlockingQueue<>();
        private final Encoding encoding;

        EventTransport(Encoding encoding) {
            this.encoding = encoding;
        }

        @Override
        public Encoding encoding() {
            return encoding;
        }

        @Override
        public int messageMaxBytes() {
            return 500_000;
        }

        @Override
        public int messageSizeInBytes(List<byte[]> encodedSpans) {
            return encoding.listSizeInBytes(encodedSpans);
        }

        @Override
        public int messageSizeInBytes(int encodedSizeInBytes) {
            return encoding.listSizeInBytes(encodedSizeInBytes);
        }

        @Override
        public void send(List<byte[]> encodedSpans) {
            senderThreads.add(Thread.currentThread());
            messages.add(encoding.encode(encodedSpans));
        }

        @Override
        public void close() {
        }
    }
}
