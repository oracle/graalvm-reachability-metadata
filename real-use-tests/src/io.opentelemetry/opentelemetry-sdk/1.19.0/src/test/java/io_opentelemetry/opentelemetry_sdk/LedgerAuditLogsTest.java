/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.LogLimits;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the logs SDK: an `SdkLoggerProvider` emitting log records and events through a
 * composite of an enriching `LogRecordProcessor`, a `SimpleLogRecordProcessor` and a
 * `BatchLogRecordProcessor`, under `LogLimits`, a provider `Clock` and a provider resource.
 *
 * A ledger service writes an audit trail of money transfers. Every record emitted while a
 * tenant is in the baggage is stamped with that tenant by the application's own processor,
 * then exported immediately to an audit sink and in batches to a long-term archive. Records
 * written inside a transfer span carry that span's trace and span ids, events carry their
 * domain and name, and records without an explicit timestamp take the provider clock's time.
 * Oversized audit records are cut down to the configured limits, and shutting the provider
 * down drains the archive queue, reports how many records the batch processor handled and
 * stops accepting new records.
 */
class LedgerAuditLogsTest {

    private static final long CLOCK_EPOCH_NANOS = TimeUnit.SECONDS.toNanos(1_700_000_000L);

    private static final String SCHEMA_URL = "https://opentelemetry.io/schemas/1.13.0";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> TENANT = AttributeKey.stringKey("ledger.tenant");

    private static final AttributeKey<String> ACCOUNT = AttributeKey.stringKey("ledger.account");

    private static final AttributeKey<Long> AMOUNT_CENTS = AttributeKey.longKey("ledger.amount_cents");

    private static final AttributeKey<String> MEMO = AttributeKey.stringKey("ledger.memo");

    private static final AttributeKey<String> CURRENCY = AttributeKey.stringKey("ledger.currency");

    private static final AttributeKey<String> CHANNEL = AttributeKey.stringKey("ledger.channel");

    private static final AttributeKey<Boolean> FLAGGED = AttributeKey.booleanKey("ledger.flagged");

    private static final AttributeKey<String> EVENT_DOMAIN = AttributeKey.stringKey("event.domain");

    private static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");

    private InMemoryLogRecordExporter auditSink;

    private ArchiveExporter archive;

    private InMemoryMetricReader metricReader;

    private SdkMeterProvider meterProvider;

    private SdkTracerProvider tracerProvider;

    private SdkLoggerProvider loggerProvider;

    @BeforeEach
    void setUp() {
        auditSink = InMemoryLogRecordExporter.create();
        archive = new ArchiveExporter();
        metricReader = InMemoryMetricReader.create();
        meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();
        tracerProvider = SdkTracerProvider.builder().build();
        BatchLogRecordProcessor archiveProcessor = BatchLogRecordProcessor.builder(archive)
                .setScheduleDelay(1, TimeUnit.HOURS)
                .setMeterProvider(meterProvider)
                .build();
        loggerProvider = SdkLoggerProvider.builder()
                .setResource(Resource.create(Attributes.of(SERVICE_NAME, "ledger-service")))
                .setClock(new FixedClock(CLOCK_EPOCH_NANOS))
                .setLogLimits(() -> LogLimits.builder()
                        .setMaxNumberOfAttributes(4)
                        .setMaxAttributeValueLength(20)
                        .build())
                .addLogRecordProcessor(LogRecordProcessor.composite(
                        new TenantStampingProcessor(),
                        SimpleLogRecordProcessor.create(auditSink),
                        archiveProcessor))
                .build();
    }

    @AfterEach
    void tearDown() {
        loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
        meterProvider.shutdown().join(10, TimeUnit.SECONDS);
    }

    @Test
    void transferAuditTrailCarriesTraceContextTenantScopeAndClockTime() {
        Logger transfers = loggerProvider.loggerBuilder("ledger.transfers")
                .setInstrumentationVersion("3.1.0")
                .setSchemaUrl(SCHEMA_URL)
                .setEventDomain("ledger")
                .build();
        Logger undomained = loggerProvider.get("ledger.legacy");
        Tracer tracer = tracerProvider.get("ledger.transfers");
        Instant acceptedAt = Instant.parse("2024-03-01T09:15:30.250Z");

        SpanContext transferSpan;
        Baggage tenantBaggage = Baggage.builder().put("tenant", "acme").build();
        try (Scope tenantScope = tenantBaggage.storeInContext(Context.current()).makeCurrent()) {
            Span span = tracer.spanBuilder("transfer").startSpan();
            transferSpan = span.getSpanContext();
            try (Scope spanScope = span.makeCurrent()) {
                transfers.logRecordBuilder()
                        .setContext(Context.current())
                        .setEpoch(acceptedAt)
                        .setSeverity(Severity.INFO)
                        .setSeverityText("INFO")
                        .setBody("transfer accepted")
                        .setAttribute(ACCOUNT, "DE-0042")
                        .setAttribute(AMOUNT_CENTS, 125_00L)
                        .emit();
                transfers.eventBuilder("transfer.rejected")
                        .setContext(Context.current())
                        .setSeverity(Severity.WARN)
                        .setBody("daily limit exceeded")
                        .setAttribute(ACCOUNT, "DE-0042")
                        .emit();
                undomained.eventBuilder("transfer.ignored").setBody("no domain").emit();
            } finally {
                span.end();
            }
        }
        transfers.logRecordBuilder()
                .setSeverity(Severity.DEBUG)
                .setBody("ledger reconciled")
                .emit();
        transfers.logRecordBuilder().setSeverity(Severity.TRACE).emit();

        List<LogRecordData> audited = auditSink.getFinishedLogItems();
        assertThat(audited).extracting(record -> record.getBody().asString())
                .containsExactly("transfer accepted", "daily limit exceeded", "ledger reconciled", "");
        assertThat(archive.exported).isEmpty();

        LogRecordData accepted = audited.get(0);
        assertThat(accepted.getResource().getAttribute(SERVICE_NAME)).isEqualTo("ledger-service");
        InstrumentationScopeInfo scope = accepted.getInstrumentationScopeInfo();
        assertThat(scope.getName()).isEqualTo("ledger.transfers");
        assertThat(scope.getVersion()).isEqualTo("3.1.0");
        assertThat(scope.getSchemaUrl()).isEqualTo(SCHEMA_URL);
        assertThat(accepted.getEpochNanos()).isEqualTo(
                TimeUnit.SECONDS.toNanos(acceptedAt.getEpochSecond()) + acceptedAt.getNano());
        assertThat(accepted.getSeverity()).isEqualTo(Severity.INFO);
        assertThat(accepted.getSeverityText()).isEqualTo("INFO");
        assertThat(accepted.getBody().getType()).isEqualTo(Body.Type.STRING);
        assertThat(accepted.getSpanContext().getTraceId()).isEqualTo(transferSpan.getTraceId());
        assertThat(accepted.getSpanContext().getSpanId()).isEqualTo(transferSpan.getSpanId());
        assertThat(accepted.getAttributes().asMap()).containsOnly(
                entry(ACCOUNT, "DE-0042"), entry(AMOUNT_CENTS, 125_00L), entry(TENANT, "acme"));

        LogRecordData rejected = audited.get(1);
        assertThat(rejected.getSeverity()).isEqualTo(Severity.WARN);
        assertThat(rejected.getSpanContext()).isEqualTo(transferSpan);
        assertThat(rejected.getEpochNanos()).isEqualTo(CLOCK_EPOCH_NANOS);
        assertThat(rejected.getAttributes().get(EVENT_DOMAIN)).isEqualTo("ledger");
        assertThat(rejected.getAttributes().get(EVENT_NAME)).isEqualTo("transfer.rejected");
        assertThat(rejected.getAttributes().get(TENANT)).isEqualTo("acme");

        LogRecordData reconciled = audited.get(2);
        assertThat(reconciled.getSpanContext().isValid()).isFalse();
        assertThat(reconciled.getEpochNanos()).isEqualTo(CLOCK_EPOCH_NANOS);
        assertThat(reconciled.getAttributes().isEmpty()).isTrue();

        LogRecordData bodiless = audited.get(3);
        assertThat(bodiless.getBody().getType()).isEqualTo(Body.Type.EMPTY);
        assertThat(bodiless.getSeverity()).isEqualTo(Severity.TRACE);

        assertThat(loggerProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(archive.exported).extracting(record -> record.getBody().asString())
                .containsExactly("transfer accepted", "daily limit exceeded", "ledger reconciled", "");
        assertThat(archive.exported.get(0).getAttributes().get(TENANT)).isEqualTo("acme");
    }

    @Test
    void oversizedAuditRecordIsCutDownToTheConfiguredLimits() {
        Logger bulk = loggerProvider.get("ledger.bulk-import");

        try (Scope tenantScope = Baggage.builder().put("tenant", "globex").build()
                .storeInContext(Context.current()).makeCurrent()) {
            bulk.logRecordBuilder()
                    .setSeverity(Severity.ERROR)
                    .setBody("bulk import row rejected")
                    .setAttribute(MEMO, "quarterly settlement for vendor invoices")
                    .setAttribute(ACCOUNT, "FR-7781")
                    .setAttribute(CURRENCY, "EUR")
                    .setAttribute(AMOUNT_CENTS, 9_999_99L)
                    .setAttribute(CHANNEL, "sftp-batch")
                    .setAttribute(FLAGGED, true)
                    .emit();
        }
        bulk.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody("bulk import finished")
                .setAllAttributes(Attributes.of(CHANNEL, "sftp-batch-nightly-run"))
                .emit();

        List<LogRecordData> audited = auditSink.getFinishedLogItems();
        assertThat(audited).hasSize(2);

        LogRecordData rejectedRow = audited.get(0);
        assertThat(rejectedRow.getAttributes().size()).isEqualTo(4);
        assertThat(rejectedRow.getTotalAttributeCount()).isEqualTo(7);
        assertThat(rejectedRow.getAttributes().asMap()).containsOnly(
                entry(MEMO, "quarterly settlement"),
                entry(ACCOUNT, "FR-7781"),
                entry(CURRENCY, "EUR"),
                entry(AMOUNT_CENTS, 9_999_99L));
        assertThat(rejectedRow.getAttributes().get(TENANT)).isNull();

        LogRecordData finished = audited.get(1);
        assertThat(finished.getAttributes().asMap()).containsOnly(entry(CHANNEL, "sftp-batch-nightly-r"));
        assertThat(finished.getTotalAttributeCount()).isEqualTo(1);
    }

    @Test
    void shutdownDrainsTheArchiveReportsProcessedRecordsAndStopsEmitting() {
        Logger settlements = loggerProvider.get("ledger.settlements");
        for (int batch = 1; batch <= 3; batch++) {
            settlements.logRecordBuilder()
                    .setSeverity(Severity.INFO)
                    .setBody("settlement batch " + batch + " posted")
                    .emit();
        }
        assertThat(auditSink.getFinishedLogItems()).hasSize(3);
        assertThat(archive.exported).isEmpty();

        assertThat(loggerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();

        assertThat(archive.exported).extracting(record -> record.getBody().asString()).containsExactly(
                "settlement batch 1 posted", "settlement batch 2 posted", "settlement batch 3 posted");
        assertThat(archive.shutDown).isTrue();

        MetricData processed = metricReader.collectAllMetrics().stream()
                .filter(metric -> metric.getName().equals("processedLogs"))
                .findFirst()
                .orElseThrow();
        assertThat(processed.getInstrumentationScopeInfo().getName()).isEqualTo("io.opentelemetry.sdk.logs");
        List<LongPointData> points = new ArrayList<>(processed.getLongSumData().getPoints());
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getValue()).isEqualTo(3L);
        assertThat(points.get(0).getAttributes().get(AttributeKey.stringKey("logRecordProcessorType")))
                .isEqualTo("BatchLogRecordProcessor");
        assertThat(points.get(0).getAttributes().get(AttributeKey.booleanKey("dropped"))).isFalse();

        settlements.logRecordBuilder().setBody("late settlement").emit();
        assertThat(loggerProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isFalse();
        assertThat(archive.exported).hasSize(3);
    }

    private static <T> Map.Entry<AttributeKey<?>, Object> entry(AttributeKey<T> key, T value) {
        return Map.entry(key, value);
    }

    /** Stamps each record with the tenant the current baggage names, as the ledger's own enrichment step. */
    private static final class TenantStampingProcessor implements LogRecordProcessor {

        @Override
        public void onEmit(ReadWriteLogRecord logRecord) {
            String tenant = Baggage.current().getEntryValue("tenant");
            if (tenant != null) {
                logRecord.setAttribute(TENANT, tenant);
            }
        }
    }

    /** Long-term archive the batch processor exports to; it keeps every record it receives. */
    private static final class ArchiveExporter implements LogRecordExporter {

        private final List<LogRecordData> exported = new CopyOnWriteArrayList<>();

        private volatile boolean shutDown;

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            exported.addAll(logs);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutDown = true;
            return CompletableResultCode.ofSuccess();
        }
    }

    /** Ledger clock pinned to the start of the business day so record timestamps are reproducible. */
    private static final class FixedClock implements Clock {

        private final long epochNanos;

        FixedClock(long epochNanos) {
            this.epochNanos = epochNanos;
        }

        @Override
        public long now() {
            return epochNanos;
        }

        @Override
        public long nanoTime() {
            return epochNanos;
        }
    }
}
