/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.AwaitableCallback;
import zipkin2.reporter.Call;
import zipkin2.reporter.Callback;
import zipkin2.reporter.CheckResult;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Sender;
import zipkin2.reporter.SpanBytesEncoder;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * The zipkin-reporter 2.x `Sender` API kept for existing applications: an application `Sender`
 * whose `sendSpans` returns a `Call`, `Call.Base` execute, enqueue, cancel and clone semantics,
 * `Callback` and `AwaitableCallback` completion, the `map`, `flatMap` and `handleError`
 * combinators, and `check()` health probes answered with a `CheckResult`.
 *
 * A billing service wrote a spool sender against the 2.x API: every message lands as a JSON file
 * in a directory a log shipper forwards to Zipkin. After upgrading to 3.4.0 the same class still
 * plugs into `AsyncReporter`, its ops endpoint still probes `check()`, and its batch jobs still
 * send through `Call`s. The service also probes the bundled `URLConnectionSender` through the same
 * legacy `Sender` view, against a collector played by a JDK `HttpServer`.
 */
public class LegacySenderCallApiTest {

    private static final String TRACE_ID = "6b221d5bc9e6496c8f2b17a3c2d5e7f1";

    @TempDir
    Path workDir;

    @Test
    void anAsyncReporterDrivesTheLegacySenderThroughItsCalls() throws Exception {
        Path spoolDir = Files.createDirectory(workDir.resolve("spool"));
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        SpoolDirectorySender spool = new SpoolDirectorySender(spoolDir);
        AsyncReporter<Span> reporter = AsyncReporter.builder(spool)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .metrics(metrics)
                .build(SpanBytesEncoder.JSON_V2);
        try {
            List<Span> invoice = List.of(
                    span("0000000000000001", null, "post /invoices"),
                    span("0000000000000002", "0000000000000001", "render-pdf"));
            invoice.forEach(reporter::report);
            reporter.flush();

            // The reporter sends through the legacy call, which spools one JSON list per message.
            assertEquals(1, spool.callsCreated.get());
            List<Path> files = spool.spooledFiles();
            assertEquals(1, files.size());
            assertEquals(invoice, SpanBytesDecoder.JSON_V2.decodeList(Files.readAllBytes(files.get(0))));
            assertEquals(1, metrics.messages());
            assertEquals(2, metrics.spans());

            // When the shipper's volume disappears, the message is dropped and its cause recorded.
            deleteSpool(spoolDir);
            reporter.report(span("0000000000000003", null, "post /refunds"));
            reporter.flush();
            assertEquals(2, spool.callsCreated.get());
            assertEquals(1, metrics.messagesDropped());
            assertEquals(1, metrics.spansDropped());
            Map<Class<? extends Throwable>, Long> causes = metrics.messagesDroppedByCause();
            assertEquals(Map.of(NoSuchFileException.class, 1L), causes);
        } finally {
            reporter.close();
            spool.close();
        }
    }

    @Test
    void theOpsEndpointProbesTheSpoolThroughCheck() throws Exception {
        Path spoolDir = Files.createDirectory(workDir.resolve("spool"));
        SpoolDirectorySender spool = new SpoolDirectorySender(spoolDir);

        CheckResult healthy = spool.check();
        assertTrue(healthy.ok());
        assertSame(CheckResult.OK, healthy);
        assertNull(healthy.error());

        deleteSpool(spoolDir);
        CheckResult missing = spool.check();
        assertFalse(missing.ok());
        NoSuchFileException cause = assertInstanceOf(NoSuchFileException.class, missing.error());
        assertEquals(spoolDir.toString(), cause.getFile());
        assertTrue(missing.toString().startsWith("CheckResult{ok=false, error="), missing.toString());

        // A closed sender reports itself down and refuses new calls.
        Files.createDirectory(spoolDir);
        spool.close();
        assertInstanceOf(ClosedSenderException.class, spool.check().error());
        assertThrows(ClosedSenderException.class, () -> spool.sendSpans(encode(span("0000000000000004", null, "get /"))));
    }

    @Test
    void aCallRunsOnceAndIsClonedToRetry() throws Exception {
        Path spoolDir = Files.createDirectory(workDir.resolve("spool"));
        SpoolDirectorySender spool = new SpoolDirectorySender(spoolDir);
        try {
            Span settle = span("0000000000000010", null, "settle-ledger");
            Call<Void> call = spool.sendSpans(encode(settle));
            assertNull(call.execute());
            assertEquals(1, spool.spooledFiles().size());

            IllegalStateException again = assertThrows(IllegalStateException.class, call::execute);
            assertEquals("Already Executed", again.getMessage());
            assertEquals(1, spool.spooledFiles().size());

            // The nightly job retries a batch by cloning the call it already ran.
            call.clone().execute();
            List<Path> files = spool.spooledFiles();
            assertEquals(2, files.size());
            for (Path file : files) {
                assertEquals(List.of(settle), SpanBytesDecoder.JSON_V2.decodeList(Files.readAllBytes(file)));
            }

            // A call canceled before it runs fails without touching the spool.
            Call<Void> abandoned = spool.sendSpans(encode(settle));
            abandoned.cancel();
            assertTrue(abandoned.isCanceled());
            IOException canceled = assertThrows(IOException.class, abandoned::execute);
            assertEquals("Canceled", canceled.getMessage());
            assertEquals(2, spool.spooledFiles().size());
        } finally {
            spool.close();
        }
    }

    @Test
    void enqueuedCallsCompleteThroughCallbacks() throws Exception {
        Path spoolDir = Files.createDirectory(workDir.resolve("spool"));
        SpoolDirectorySender spool = new SpoolDirectorySender(spoolDir);
        try {
            AwaitableCallback delivered = new AwaitableCallback();
            spool.sendSpans(encode(span("0000000000000020", null, "post /payments"))).enqueue(delivered);
            delivered.await();
            assertEquals(1, spool.spooledFiles().size());

            // The application's own callback sees the spool thread finish the write.
            CompletableFuture<String> completion = new CompletableFuture<>();
            spool.sendSpans(encode(span("0000000000000021", null, "post /payments"))).enqueue(new Callback<>() {
                @Override
                public void onSuccess(Void value) {
                    completion.complete(Thread.currentThread().getName());
                }

                @Override
                public void onError(Throwable error) {
                    completion.completeExceptionally(error);
                }
            });
            assertEquals("billing-spool", completion.get(10, TimeUnit.SECONDS));
            assertEquals(2, spool.spooledFiles().size());

            // AwaitableCallback rethrows a checked failure wrapped in a RuntimeException.
            deleteSpool(spoolDir);
            AwaitableCallback failed = new AwaitableCallback();
            spool.sendSpans(encode(span("0000000000000022", null, "post /payments"))).enqueue(failed);
            RuntimeException error = assertThrows(RuntimeException.class, failed::await);
            assertInstanceOf(NoSuchFileException.class, error.getCause());
        } finally {
            spool.close();
        }
    }

    @Test
    void combinatorsMapFlatMapAndRecoverFromErrors() throws Exception {
        Path spoolDir = Files.createDirectory(workDir.resolve("spool"));
        SpoolDirectorySender spool = new SpoolDirectorySender(spoolDir);
        try {
            List<byte[]> batch = encode(
                    span("0000000000000030", null, "close-period"),
                    span("0000000000000031", "0000000000000030", "post-journal"));

            // A job logs the spool sequence number its batch landed under.
            Call<Integer> landed = spool.sendSpans(batch).map(ignored -> spool.sequence.get());
            assertEquals(1, landed.execute());

            // A batch computed lazily is chained into the send that ships it.
            Call<Void> deferred = Call.create(batch).flatMap(spool::sendSpans);
            assertNull(deferred.execute());
            assertEquals(2, spool.spooledFiles().size());
            assertEquals("ConstantCall{value=done}", Call.create("done").toString());
            assertEquals(List.of(), Call.<String>emptyList().execute());

            // Best-effort sends swallow the failure after recording it for the next health report.
            deleteSpool(spoolDir);
            List<Throwable> recorded = new CopyOnWriteArrayList<>();
            Call<Void> tolerant = spool.sendSpans(batch).handleError((error, callback) -> {
                recorded.add(error);
                callback.onSuccess(null);
            });
            assertNull(tolerant.execute());
            assertEquals(1, recorded.size());
            assertInstanceOf(NoSuchFileException.class, recorded.get(0));

            AwaitableCallback recovered = new AwaitableCallback();
            spool.sendSpans(batch).handleError((error, callback) -> {
                recorded.add(error);
                callback.onSuccess(null);
            }).enqueue(recovered);
            recovered.await();
            assertEquals(2, recorded.size());
        } finally {
            spool.close();
        }
    }

    @Test
    void theBundledHttpSenderAnswersTheLegacyHealthProbe() throws Exception {
        BlockingQueue<byte[]> posts = new LinkedBlockingQueue<>();
        AtomicBoolean maintenance = new AtomicBoolean();
        HttpServer collector = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        collector.createContext("/api/v2/spans", exchange -> receive(exchange, posts, maintenance));
        collector.start();
        String endpoint = "http://127.0.0.1:" + collector.getAddress().getPort() + "/api/v2/spans";
        Sender sender = URLConnectionSender.newBuilder().endpoint(endpoint).compressionEnabled(false).build();
        try {
            assertEquals(Encoding.JSON, sender.encoding());
            assertEquals("URLConnectionSender{" + endpoint + "}", sender.toString());

            // The probe posts an empty list, which a healthy collector accepts.
            CheckResult up = sender.check();
            assertTrue(up.ok(), up.toString());
            assertEquals(List.of(), SpanBytesDecoder.JSON_V2.decodeList(nextPost(posts)));

            Span lookup = span("0000000000000040", null, "get /accounts");
            sender.sendSpans(encode(lookup)).execute();
            assertEquals(List.of(lookup), SpanBytesDecoder.JSON_V2.decodeList(nextPost(posts)));

            // While the collector is in maintenance the probe reports the HTTP failure.
            maintenance.set(true);
            CheckResult down = sender.check();
            assertFalse(down.ok());
            assertInstanceOf(IOException.class, down.error());
            AwaitableCallback rejected = new AwaitableCallback();
            sender.sendSpans(encode(lookup)).enqueue(rejected);
            RuntimeException error = assertThrows(RuntimeException.class, rejected::await);
            assertInstanceOf(IOException.class, error.getCause());

            sender.close();
            assertInstanceOf(ClosedSenderException.class, sender.check().error());
        } finally {
            sender.close();
            collector.stop(0);
        }
    }

    private static void receive(HttpExchange exchange, BlockingQueue<byte[]> posts, AtomicBoolean maintenance)
            throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readAllBytes();
            if (!maintenance.get()) {
                posts.add(body);
            }
        }
        exchange.sendResponseHeaders(maintenance.get() ? 503 : 202, -1);
        exchange.close();
    }

    private static byte[] nextPost(BlockingQueue<byte[]> posts) throws InterruptedException {
        byte[] body = posts.poll(10, TimeUnit.SECONDS);
        assertNotNull(body);
        return body;
    }

    private static void deleteSpool(Path spoolDir) throws IOException {
        try (Stream<Path> files = Files.list(spoolDir)) {
            for (Path file : files.collect(Collectors.toList())) {
                Files.delete(file);
            }
        }
        Files.delete(spoolDir);
    }

    private static List<byte[]> encode(Span... spans) {
        return Stream.of(spans).map(SpanBytesEncoder.JSON_V2::encode).collect(Collectors.toList());
    }

    private static Span span(String id, String parentId, String name) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(parentId)
                .id(id)
                .name(name)
                .kind(Span.Kind.SERVER)
                .localEndpoint(Endpoint.newBuilder().serviceName("billing").ip("10.0.8.12").build())
                .timestamp(1_700_000_000_000_000L)
                .duration(2_400L)
                .putTag("billing.region", "eu-west")
                .build();
    }

    /// The billing service's 2.x-era sender: one JSON list file per message in a spool directory.
    private static final class SpoolDirectorySender extends Sender {

        private final Path spoolDir;
        private final ExecutorService spoolThread = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "billing-spool");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicInteger callsCreated = new AtomicInteger();
        private volatile boolean closed;

        SpoolDirectorySender(Path spoolDir) {
            this.spoolDir = spoolDir;
        }

        @Override
        public Encoding encoding() {
            return Encoding.JSON;
        }

        @Override
        public int messageMaxBytes() {
            return 64 * 1024;
        }

        @Override
        public int messageSizeInBytes(List<byte[]> encodedSpans) {
            return Encoding.JSON.listSizeInBytes(encodedSpans);
        }

        @Override
        public Call<Void> sendSpans(List<byte[]> encodedSpans) {
            if (closed) {
                throw new ClosedSenderException();
            }
            callsCreated.incrementAndGet();
            return new SpoolCall(encodedSpans);
        }

        @Override
        public CheckResult check() {
            if (closed) {
                return CheckResult.failed(new ClosedSenderException());
            }
            if (!Files.isDirectory(spoolDir)) {
                return CheckResult.failed(new NoSuchFileException(spoolDir.toString()));
            }
            return CheckResult.OK;
        }

        @Override
        public void close() {
            closed = true;
            spoolThread.shutdown();
        }

        List<Path> spooledFiles() throws IOException {
            try (Stream<Path> files = Files.list(spoolDir)) {
                return files.sorted().collect(Collectors.toList());
            }
        }

        /// Writes one message; `Call.Base` guards against double execution and cancellation.
        private final class SpoolCall extends Call.Base<Void> {

            private final List<byte[]> encodedSpans;

            SpoolCall(List<byte[]> encodedSpans) {
                this.encodedSpans = encodedSpans;
            }

            @Override
            protected Void doExecute() throws IOException {
                String name = String.format("spans-%04d.json", sequence.incrementAndGet());
                Files.write(spoolDir.resolve(name), Encoding.JSON.encode(encodedSpans), StandardOpenOption.CREATE_NEW);
                return null;
            }

            @Override
            protected void doEnqueue(Callback<Void> callback) {
                spoolThread.execute(() -> {
                    try {
                        doExecute();
                    } catch (Throwable error) {
                        Call.propagateIfFatal(error);
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(null);
                });
            }

            @Override
            public Call<Void> clone() {
                return new SpoolCall(encodedSpans);
            }
        }
    }
}
