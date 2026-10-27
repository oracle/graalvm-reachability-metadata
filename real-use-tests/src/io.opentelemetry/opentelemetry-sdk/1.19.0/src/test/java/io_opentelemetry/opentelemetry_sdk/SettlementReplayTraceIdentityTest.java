/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises trace identity and timekeeping in the trace SDK: an application `IdGenerator` and
 * `Clock` installed on the `SdkTracerProvider`, explicit start, event and end timestamps, child
 * spans timed by their parent's anchored clock, and in-flight spans observed and enriched as
 * `ReadWriteSpan`s by a `SpanProcessor` until they end and after the provider shuts down.
 *
 * A settlement service replays recorded ledger batches. Each replayed trace carries a trace ID
 * derived from the batch number, so the ledger and the trace backend can be joined, and span IDs
 * come from the replay's own sequence. The replay clock advances only when the journal says time
 * passed, so every exported timestamp and duration is exact. A supervisor processor tracks the
 * spans still open, stamps the worker that runs them and lets the service read their running
 * latency, name and attributes before they are exported.
 */
class SettlementReplayTraceIdentityTest {

    private static final String SCOPE_NAME = "ledger.settlement.replay";

    private static final String SCOPE_VERSION = "3.1.0";

    private static final Instant REPLAY_START = Instant.parse("2026-03-01T09:00:00Z");

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<Long> BATCH = AttributeKey.longKey("ledger.batch");

    private static final AttributeKey<String> ENTRY = AttributeKey.stringKey("ledger.entry");

    private static final AttributeKey<Long> AMOUNT_CENTS = AttributeKey.longKey("ledger.amount_cents");

    private static final AttributeKey<String> WORKER = AttributeKey.stringKey("replay.worker");

    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("replay.outcome");

    private ReplayClock clock;

    private LedgerIdGenerator ids;

    private ReplaySupervisor supervisor;

    private InMemorySpanExporter exporter;

    private SdkTracerProvider tracerProvider;

    private Tracer tracer;

    @BeforeEach
    void setUp() {
        clock = new ReplayClock(REPLAY_START);
        ids = new LedgerIdGenerator();
        supervisor = new ReplaySupervisor("replay-worker-7");
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(
                        Resource.create(Attributes.of(SERVICE_NAME, "settlement-replay"))))
                .setClock(clock)
                .setIdGenerator(ids)
                .addSpanProcessor(supervisor)
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        tracer = tracerProvider.tracerBuilder(SCOPE_NAME).setInstrumentationVersion(SCOPE_VERSION).build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
    }

    @Test
    void replayedBatchCarriesLedgerIdsAndRecordedTimes() {
        Instant firstPosted = REPLAY_START.plusMillis(120);
        Instant secondPosted = REPLAY_START.plusMillis(480);

        ids.beginBatch(42);
        Span batch = tracer.spanBuilder("settle batch")
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute(BATCH, 42L)
                .startSpan();
        try (Scope ignored = batch.makeCurrent()) {
            postEntry("E-1001", 12_500L, REPLAY_START.plusMillis(100), firstPosted, REPLAY_START.plusMillis(150));
            postEntry("E-1002", 7_250L, REPLAY_START.plusMillis(400), secondPosted, REPLAY_START.plusMillis(510));

            clock.advance(Duration.ofSeconds(1));
            Span verify = tracer.spanBuilder("verify totals").startSpan();
            clock.advance(Duration.ofMillis(200));
            verify.end();

            clock.advance(Duration.ofMillis(300));
        } finally {
            batch.end();
        }

        Map<String, SpanData> spans = exportedByName();
        assertThat(spans).containsOnlyKeys("settle batch", "post E-1001", "post E-1002", "verify totals");

        String batchTraceId = LedgerIdGenerator.traceIdFor(42);
        SpanData root = spans.get("settle batch");
        assertThat(root.getTraceId()).isEqualTo(batchTraceId);
        assertThat(root.getSpanId()).isEqualTo("0000000000000001");
        assertThat(root.getParentSpanContext().isValid()).isFalse();
        assertThat(root.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(root.getStartEpochNanos()).isEqualTo(nanos(REPLAY_START));
        assertThat(root.getEndEpochNanos()).isEqualTo(nanos(REPLAY_START.plusMillis(1_500)));
        assertThat(root.getResource().getAttribute(SERVICE_NAME)).isEqualTo("settlement-replay");
        assertThat(root.getInstrumentationScopeInfo().getName()).isEqualTo(SCOPE_NAME);
        assertThat(root.getInstrumentationScopeInfo().getVersion()).isEqualTo(SCOPE_VERSION);

        SpanData first = spans.get("post E-1001");
        assertThat(first.getTraceId()).isEqualTo(batchTraceId);
        assertThat(first.getSpanId()).isEqualTo("0000000000000002");
        assertThat(first.getParentSpanId()).isEqualTo(root.getSpanId());
        assertThat(first.getStartEpochNanos()).isEqualTo(nanos(REPLAY_START.plusMillis(100)));
        assertThat(first.getEndEpochNanos()).isEqualTo(nanos(REPLAY_START.plusMillis(150)));
        assertThat(first.getAttributes().get(AMOUNT_CENTS)).isEqualTo(12_500L);
        EventData posted = first.getEvents().get(0);
        assertThat(posted.getName()).isEqualTo("ledger.posted");
        assertThat(posted.getEpochNanos()).isEqualTo(nanos(firstPosted));
        assertThat(posted.getAttributes().get(ENTRY)).isEqualTo("E-1001");

        SpanData second = spans.get("post E-1002");
        assertThat(second.getSpanId()).isEqualTo("0000000000000003");
        assertThat(second.getParentSpanId()).isEqualTo(root.getSpanId());
        assertThat(second.getEndEpochNanos() - second.getStartEpochNanos())
                .isEqualTo(TimeUnit.MILLISECONDS.toNanos(110));
        assertThat(second.getEvents()).extracting(EventData::getEpochNanos).containsExactly(nanos(secondPosted));

        SpanData verify = spans.get("verify totals");
        assertThat(verify.getSpanId()).isEqualTo("0000000000000004");
        assertThat(verify.getParentSpanId()).isEqualTo(root.getSpanId());
        assertThat(verify.getStartEpochNanos()).isEqualTo(nanos(REPLAY_START.plusMillis(1_000)));
        assertThat(verify.getEndEpochNanos()).isEqualTo(nanos(REPLAY_START.plusMillis(1_200)));

        assertThat(spans.values()).allSatisfy(span -> assertThat(span.getAttributes().get(WORKER))
                .isEqualTo("replay-worker-7"));
        assertThat(ids.traceIdsIssued()).isEqualTo(1);
        assertThat(supervisor.openSpans()).isEmpty();
    }

    @Test
    void separateBatchesGetSeparateLedgerTraces() {
        for (long batchNumber : new long[] {7, 8}) {
            ids.beginBatch(batchNumber);
            Span batch = tracer.spanBuilder("settle batch").setAttribute(BATCH, batchNumber).startSpan();
            clock.advance(Duration.ofMillis(50));
            batch.end();
        }

        List<SpanData> finished = exporter.getFinishedSpanItems();
        assertThat(finished).extracting(SpanData::getTraceId)
                .containsExactly(LedgerIdGenerator.traceIdFor(7), LedgerIdGenerator.traceIdFor(8));
        assertThat(finished).extracting(span -> span.getAttributes().get(BATCH)).containsExactly(7L, 8L);
        assertThat(finished).extracting(SpanData::getStartEpochNanos)
                .containsExactly(nanos(REPLAY_START), nanos(REPLAY_START.plusMillis(50)));
        assertThat(finished).allSatisfy(span -> assertThat(span.getEndEpochNanos() - span.getStartEpochNanos())
                .isEqualTo(TimeUnit.MILLISECONDS.toNanos(50)));
        assertThat(ids.traceIdsIssued()).isEqualTo(2);
    }

    @Test
    void supervisorObservesOpenSpanUntilItEnds() {
        ids.beginBatch(99);
        Span batch = tracer.spanBuilder("settle batch").startSpan();

        ReadWriteSpan open = supervisor.openSpans().get(batch.getSpanContext().getSpanId());
        assertThat(open).isNotNull();
        assertThat(open.hasEnded()).isFalse();
        assertThat(open.getAttribute(WORKER)).isEqualTo("replay-worker-7");
        assertThat(open.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(open.getSpanContext()).isEqualTo(batch.getSpanContext());

        clock.advance(Duration.ofMillis(250));
        assertThat(open.getLatencyNanos()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(250));

        batch.updateName("settle batch 99");
        batch.setAttribute(BATCH, 99L);
        assertThat(open.getName()).isEqualTo("settle batch 99");
        assertThat(open.getAttribute(BATCH)).isEqualTo(99L);
        SpanData snapshot = open.toSpanData();
        assertThat(snapshot.hasEnded()).isFalse();
        assertThat(snapshot.getName()).isEqualTo("settle batch 99");
        assertThat(exporter.getFinishedSpanItems()).isEmpty();

        open.setAttribute(OUTCOME, "balanced");
        clock.advance(Duration.ofMillis(50));
        batch.end();

        assertThat(open.hasEnded()).isTrue();
        assertThat(batch.isRecording()).isFalse();
        clock.advance(Duration.ofSeconds(5));
        assertThat(open.getLatencyNanos()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(300));

        batch.setAttribute(OUTCOME, "rewritten");
        batch.end();
        assertThat(supervisor.openSpans()).isEmpty();
        assertThat(supervisor.endedNames()).containsExactly("settle batch 99");

        List<SpanData> finished = exporter.getFinishedSpanItems();
        assertThat(finished).hasSize(1);
        SpanData exported = finished.get(0);
        assertThat(exported.getName()).isEqualTo("settle batch 99");
        assertThat(exported.getAttributes().get(OUTCOME)).isEqualTo("balanced");
        assertThat(exported.getEndEpochNanos()).isEqualTo(nanos(REPLAY_START.plusMillis(300)));
    }

    @Test
    void shutdownProviderStopsIssuingLedgerIds() {
        ids.beginBatch(5);
        Span before = tracer.spanBuilder("settle batch").startSpan();
        before.end();
        assertThat(ids.spanIdsIssued()).isEqualTo(1);
        assertThat(exporter.getFinishedSpanItems()).extracting(SpanData::getTraceId)
                .containsExactly(LedgerIdGenerator.traceIdFor(5));

        assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(supervisor.isShutdown()).isTrue();

        ids.beginBatch(6);
        Span after = tracer.spanBuilder("settle batch").startSpan();
        SpanContext afterContext = after.getSpanContext();
        after.end();

        assertThat(after.isRecording()).isFalse();
        assertThat(afterContext.isValid()).isFalse();
        assertThat(ids.spanIdsIssued()).isEqualTo(1);
        assertThat(ids.traceIdsIssued()).isEqualTo(1);
        assertThat(supervisor.endedNames()).containsExactly("settle batch");
    }

    private void postEntry(String entry, long amountCents, Instant start, Instant posted, Instant end) {
        Span span = tracer.spanBuilder("post " + entry)
                .setParent(Context.current())
                .setStartTimestamp(start)
                .setAttribute(ENTRY, entry)
                .setAttribute(AMOUNT_CENTS, amountCents)
                .startSpan();
        span.addEvent("ledger.posted", Attributes.of(ENTRY, entry), posted);
        span.end(end);
    }

    private Map<String, SpanData> exportedByName() {
        return exporter.getFinishedSpanItems().stream()
                .collect(Collectors.toMap(SpanData::getName, Function.identity()));
    }

    private static long nanos(Instant instant) {
        return TimeUnit.SECONDS.toNanos(instant.getEpochSecond()) + instant.getNano();
    }

    /** Clock that moves only when the replayed journal says time has passed. */
    static final class ReplayClock implements Clock {

        private final AtomicLong epochNanos;

        ReplayClock(Instant start) {
            epochNanos = new AtomicLong(nanos(start));
        }

        void advance(Duration duration) {
            epochNanos.addAndGet(duration.toNanos());
        }

        @Override
        public long now() {
            return epochNanos.get();
        }

        @Override
        public long nanoTime() {
            return epochNanos.get();
        }
    }

    /** Derives trace IDs from the ledger batch number and span IDs from the replay sequence. */
    static final class LedgerIdGenerator implements IdGenerator {

        private static final long LEDGER_PREFIX = 0x5e771e0000000000L;

        private final AtomicLong batchNumber = new AtomicLong();

        private final AtomicLong spanSequence = new AtomicLong();

        private final AtomicLong traceIdsIssued = new AtomicLong();

        static String traceIdFor(long batch) {
            return String.format("%016x%016x", LEDGER_PREFIX, batch);
        }

        void beginBatch(long batch) {
            batchNumber.set(batch);
        }

        @Override
        public String generateTraceId() {
            traceIdsIssued.incrementAndGet();
            return traceIdFor(batchNumber.get());
        }

        @Override
        public String generateSpanId() {
            return String.format("%016x", spanSequence.incrementAndGet());
        }

        long traceIdsIssued() {
            return traceIdsIssued.get();
        }

        long spanIdsIssued() {
            return spanSequence.get();
        }
    }

    /** Tracks the spans a replay worker has open and stamps each with the worker's name. */
    static final class ReplaySupervisor implements SpanProcessor {

        private final String worker;

        private final Map<String, ReadWriteSpan> open = new ConcurrentHashMap<>();

        private final List<String> ended = new CopyOnWriteArrayList<>();

        private volatile boolean shutdown;

        ReplaySupervisor(String worker) {
            this.worker = worker;
        }

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
            span.setAttribute(WORKER, worker);
            open.put(span.getSpanContext().getSpanId(), span);
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            open.remove(span.getSpanContext().getSpanId());
            ended.add(span.getName());
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }

        Map<String, ReadWriteSpan> openSpans() {
            return Map.copyOf(open);
        }

        List<String> endedNames() {
            return List.copyOf(ended);
        }

        boolean isShutdown() {
            return shutdown;
        }
    }
}
