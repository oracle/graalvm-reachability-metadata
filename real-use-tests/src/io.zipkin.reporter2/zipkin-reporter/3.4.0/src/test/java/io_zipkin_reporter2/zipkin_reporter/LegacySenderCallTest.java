/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.AwaitableCallback;
import zipkin2.reporter.Call;
import zipkin2.reporter.Callback;
import zipkin2.reporter.CheckResult;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Sender;
import zipkin2.reporter.SpanBytesEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uploads Thrift-encoded spans through an application `Sender` whose uploads are `Call` objects
 * that the application executes, enqueues with a `Callback`, composes, and health-checks.
 *
 * The application implements the `Sender` contract the way pre-3.x transports did: each
 * `sendSpans` returns a `Call.Base` that archives the Thrift message synchronously or on a
 * worker pool. The scenarios report spans through an `AsyncReporter` on top of that sender,
 * compose upload calls with `map` and `flatMap` and wait on them with `AwaitableCallback`, and
 * take the archive offline so the health check fails, a `handleError` fallback answers in
 * place of the upload, and the reporter counts the dropped message.
 */
@SuppressWarnings("deprecation")
public class LegacySenderCallTest {

    private static final String TRACE_ID = "6b221d5bc9e6496c";
    private static final Endpoint BILLING = Endpoint.newBuilder()
            .serviceName("billing")
            .ip("172.16.4.11")
            .port(9411)
            .build();

    private ExecutorService uploadPool;
    private ArchiveSender sender;

    @BeforeEach
    void openArchive() {
        uploadPool = Executors.newSingleThreadExecutor();
        sender = new ArchiveSender(uploadPool);
    }

    @AfterEach
    void closeArchive() throws IOException, InterruptedException {
        sender.close();
        uploadPool.shutdownNow();
        assertTrue(uploadPool.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void reporterDeliversThriftMessagesThroughSender() throws Exception {
        List<Span> spans = invoiceSpans();
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();

        assertTrue(sender.check().ok(), "archive is reachable before reporting");
        try (AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .metrics(metrics)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build()) {
            spans.forEach(reporter::report);
            reporter.flush();
        }

        byte[] message = sender.archived.poll(10, TimeUnit.SECONDS);
        assertNotNull(message, "flush uploads one Thrift message");
        assertEquals(spans, SpanBytesDecoder.THRIFT.decodeList(message));
        assertEquals(spans.size(), metrics.spans());
        assertEquals(1, metrics.messages());
        assertEquals(0, metrics.messagesDropped());
    }

    @Test
    void composedCallsReportUploadOutcome() throws Exception {
        List<byte[]> encoded = encode(invoiceSpans());

        Call<Void> upload = sender.sendSpans(encoded);
        Call<Integer> archivedCount = upload.map(ignored -> sender.archived.size());
        assertEquals(1, archivedCount.execute());
        assertThrows(IllegalStateException.class, archivedCount::execute,
                "a call executes only once");

        Call<Integer> retry = archivedCount.clone();
        assertEquals(2, retry.execute(), "a clone uploads again");

        AwaitableCallback queued = new AwaitableCallback();
        Call.create(encoded).flatMap(sender::sendSpans).enqueue(queued);
        queued.await();
        assertEquals(3, sender.archived.size());
        assertEquals(1, sender.asyncUploads, "the enqueued upload ran on the worker pool");

        List<Span> archivedSpans = new ArrayList<>();
        for (byte[] message : sender.archived) {
            archivedSpans.addAll(SpanBytesDecoder.THRIFT.decodeList(message));
        }
        assertEquals(3 * encoded.size(), archivedSpans.size());

        Call<Void> canceled = sender.sendSpans(encoded);
        canceled.cancel();
        assertTrue(canceled.isCanceled());
    }

    @Test
    void unavailableArchiveFailsCheckAndFallsBack() throws Exception {
        List<Span> spans = invoiceSpans();
        List<byte[]> encoded = encode(spans);
        sender.online = false;

        CheckResult health = sender.check();
        assertFalse(health.ok());
        assertInstanceOf(IOException.class, health.error());
        assertEquals("archive offline", health.error().getMessage());

        AwaitableCallback failed = new AwaitableCallback();
        sender.sendSpans(encoded).enqueue(failed);
        RuntimeException awaited = assertThrows(RuntimeException.class, failed::await);
        assertInstanceOf(IOException.class, awaited.getCause());

        Call<Boolean> delivered = sender.sendSpans(encoded)
                .map(ignored -> Boolean.TRUE)
                .handleError((error, callback) -> callback.onSuccess(Boolean.FALSE));
        assertSame(Boolean.FALSE, delivered.execute(), "fallback answers for the failed upload");

        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        try (AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .metrics(metrics)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build()) {
            spans.forEach(reporter::report);
            reporter.flush();
        }
        assertTrue(sender.archived.isEmpty());
        assertEquals(1, metrics.messagesDropped());
        assertEquals(1L, metrics.messagesDroppedByCause().get(IOException.class));
        assertEquals(spans.size(), metrics.spansDropped());
    }

    private static List<byte[]> encode(List<Span> spans) {
        List<byte[]> encoded = new ArrayList<>();
        for (Span span : spans) {
            encoded.add(SpanBytesEncoder.THRIFT.encode(span));
        }
        return encoded;
    }

    private static List<Span> invoiceSpans() {
        long start = 1_710_000_000_000_000L;
        Span server = Span.newBuilder()
                .traceId(TRACE_ID)
                .id(TRACE_ID)
                .kind(Span.Kind.SERVER)
                .name("post /invoices")
                .localEndpoint(BILLING)
                .timestamp(start)
                .duration(35_000L)
                .putTag("invoice.currency", "EUR")
                .build();
        Span ledger = Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(TRACE_ID)
                .id("1f8c2f9e3a4b5d6e")
                .kind(Span.Kind.CLIENT)
                .name("insert ledger")
                .localEndpoint(BILLING)
                .remoteEndpoint(Endpoint.newBuilder().serviceName("ledger-db").build())
                .timestamp(start + 4_000L)
                .duration(9_000L)
                .putTag("db.statement", "INSERT INTO ledger")
                .build();
        return List.of(server, ledger);
    }

    /**
     * Sender that archives each Thrift message in memory; uploads run on the caller thread when
     * executed and on the worker pool when enqueued.
     */
    static final class ArchiveSender extends Sender {

        final BlockingQueue<byte[]> archived = new LinkedBlockingQueue<>();
        volatile boolean online = true;
        volatile int asyncUploads;
        private final ExecutorService uploadPool;

        ArchiveSender(ExecutorService uploadPool) {
            this.uploadPool = uploadPool;
        }

        @Override
        public Encoding encoding() {
            return Encoding.THRIFT;
        }

        @Override
        public int messageMaxBytes() {
            return 5 * 1024 * 1024;
        }

        @Override
        public int messageSizeInBytes(List<byte[]> encodedSpans) {
            return encoding().listSizeInBytes(encodedSpans);
        }

        @Override
        public Call<Void> sendSpans(List<byte[]> encodedSpans) {
            return new UploadCall(encoding().encode(encodedSpans));
        }

        @Override
        public CheckResult check() {
            return online ? CheckResult.OK : CheckResult.failed(new IOException("archive offline"));
        }

        void store(byte[] message) throws IOException {
            if (!online) {
                throw new IOException("archive offline");
            }
            archived.add(message);
        }

        /** One upload of an encoded Thrift message to the archive. */
        final class UploadCall extends Call.Base<Void> {

            private final byte[] message;

            UploadCall(byte[] message) {
                this.message = message;
            }

            @Override
            protected Void doExecute() throws IOException {
                store(message);
                return null;
            }

            @Override
            protected void doEnqueue(Callback<Void> callback) {
                uploadPool.execute(() -> {
                    try {
                        store(message);
                        asyncUploads++;
                        callback.onSuccess(null);
                    } catch (IOException e) {
                        callback.onError(e);
                    }
                });
            }

            @Override
            public Call<Void> clone() {
                return new UploadCall(message);
            }
        }
    }
}
