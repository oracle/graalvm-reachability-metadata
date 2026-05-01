/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_logs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
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
import io.opentelemetry.sdk.resources.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public final class Opentelemetry_sdk_logsTest {
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");
    private static final AttributeKey<String> USER_ID = AttributeKey.stringKey("user.id");
    private static final AttributeKey<Long> RETRY_COUNT = AttributeKey.longKey("retry.count");
    private static final AttributeKey<Boolean> CACHE_HIT = AttributeKey.booleanKey("cache.hit");
    private static final AttributeKey<List<String>> TAGS = AttributeKey.stringArrayKey("tags");
    private static final AttributeKey<String> PROCESSOR_MARK = AttributeKey.stringKey("processor.mark");
    private static final AttributeKey<String> EVENT_DOMAIN = AttributeKey.stringKey("event.domain");
    private static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");

    @Test
    void simpleProcessorExportsCompleteLogRecordData() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        Resource resource = Resource.create(
                Attributes.of(SERVICE_NAME, "checkout", HOST_ID, "host-a"),
                "https://schemas.example.test/resource");
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();
        SpanContext spanContext = SpanContext.create(
                "00000000000000000000000000000001",
                "0000000000000002",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        Context context = Span.wrap(spanContext).storeInContext(Context.root());
        Instant timestamp = Instant.ofEpochSecond(1_667_000_000L, 123_456_789L);

        try {
            Logger logger = loggerProvider.loggerBuilder("test.instrumentation")
                    .setInstrumentationVersion("2.3.4")
                    .setSchemaUrl("https://schemas.example.test/logs")
                    .build();

            logger.logRecordBuilder()
                    .setEpoch(timestamp)
                    .setContext(context)
                    .setSeverity(Severity.ERROR3)
                    .setSeverityText("error-three")
                    .setBody("payment authorization failed")
                    .setAttribute(USER_ID, "user-42")
                    .setAttribute(RETRY_COUNT, 3L)
                    .setAttribute(CACHE_HIT, false)
                    .setAttribute(TAGS, Arrays.asList("payments", "critical"))
                    .emit();

            LogRecordData record = singleExportedRecord(exporter);
            assertThat(record.getResource()).isEqualTo(resource);
            assertThat(record.getResource().getAttribute(SERVICE_NAME)).isEqualTo("checkout");
            assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo("test.instrumentation");
            assertThat(record.getInstrumentationScopeInfo().getVersion()).isEqualTo("2.3.4");
            assertThat(record.getInstrumentationScopeInfo().getSchemaUrl())
                    .isEqualTo("https://schemas.example.test/logs");
            assertThat(record.getEpochNanos()).isEqualTo(TimeUnit.SECONDS.toNanos(1_667_000_000L) + 123_456_789L);
            assertThat(record.getSpanContext()).isEqualTo(spanContext);
            assertThat(record.getSeverity()).isEqualTo(Severity.ERROR3);
            assertThat(record.getSeverityText()).isEqualTo("error-three");
            assertThat(record.getBody().getType()).isEqualTo(Body.Type.STRING);
            assertThat(record.getBody().asString()).isEqualTo("payment authorization failed");
            assertThat(record.getAttributes().get(USER_ID)).isEqualTo("user-42");
            assertThat(record.getAttributes().get(RETRY_COUNT)).isEqualTo(3L);
            assertThat(record.getAttributes().get(CACHE_HIT)).isFalse();
            assertThat(record.getAttributes().get(TAGS)).containsExactly("payments", "critical");
            assertThat(record.getTotalAttributeCount()).isEqualTo(4);
        } finally {
            loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void compositeLogRecordProcessorFansOutEmitsFlushAndShutdown() {
        RecordingProcessor first = new RecordingProcessor();
        RecordingProcessor second = new RecordingProcessor();
        LogRecordProcessor compositeProcessor = LogRecordProcessor.composite(Arrays.asList(first, second));
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(compositeProcessor)
                .build();

        loggerProvider.get("composite.processor.logger")
                .logRecordBuilder()
                .setBody("processed by composite")
                .setAttribute(USER_ID, "carol")
                .emit();
        CompletableResultCode flushResult = loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        CompletableResultCode shutdownResult = loggerProvider.shutdown().join(10, TimeUnit.SECONDS);

        assertThat(flushResult.isSuccess()).isTrue();
        assertThat(shutdownResult.isSuccess()).isTrue();
        assertThat(first.getRecords()).hasSize(1);
        assertThat(second.getRecords()).hasSize(1);
        assertThat(first.isFlushed()).isTrue();
        assertThat(second.isFlushed()).isTrue();
        assertThat(first.isShutdown()).isTrue();
        assertThat(second.isShutdown()).isTrue();
        assertThat(first.getRecords().get(0).getBody().asString()).isEqualTo("processed by composite");
        assertThat(second.getRecords().get(0).getAttributes().get(USER_ID)).isEqualTo("carol");
    }

    @Test
    void logRecordBuilderAddsEventAttributes() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();

        try {
            Logger logger = loggerProvider.loggerBuilder("audit.logger")
                    .build();

            logger.logRecordBuilder()
                    .setSeverity(Severity.INFO)
                    .setSeverityText("information")
                    .setAttribute(EVENT_DOMAIN, "audit")
                    .setAttribute(EVENT_NAME, "user.login")
                    .setAttribute(USER_ID, "alice")
                    .emit();

            LogRecordData record = singleExportedRecord(exporter);
            assertThat(record.getAttributes().get(EVENT_DOMAIN)).isEqualTo("audit");
            assertThat(record.getAttributes().get(EVENT_NAME)).isEqualTo("user.login");
            assertThat(record.getAttributes().get(USER_ID)).isEqualTo("alice");
            assertThat(record.getBody().getType()).isEqualTo(Body.Type.EMPTY);
            assertThat(record.getSeverity()).isEqualTo(Severity.INFO);
            assertThat(record.getTotalAttributeCount()).isEqualTo(3);
        } finally {
            loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void logRecordBuilderUsesTimeUnitEpochAndDefaultFields() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();

        try {
            loggerProvider.get("defaults.logger")
                    .logRecordBuilder()
                    .setEpoch(1_234L, TimeUnit.MILLISECONDS)
                    .setAttribute(USER_ID, "dave")
                    .emit();

            LogRecordData record = singleExportedRecord(exporter);
            assertThat(record.getEpochNanos()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(1_234L));
            assertThat(record.getSpanContext()).isEqualTo(SpanContext.getInvalid());
            assertThat(record.getSeverity()).isEqualTo(Severity.UNDEFINED_SEVERITY_NUMBER);
            assertThat(record.getSeverityText()).isNull();
            assertThat(record.getBody().getType()).isEqualTo(Body.Type.EMPTY);
            assertThat(record.getAttributes().get(USER_ID)).isEqualTo("dave");
        } finally {
            loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void logLimitsCapAttributesAndTruncateStringValues() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        LogLimits limits = LogLimits.builder()
                .setMaxNumberOfAttributes(2)
                .setMaxAttributeValueLength(5)
                .build();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setLogLimits(() -> limits)
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();

        try {
            loggerProvider.get("limited.logger")
                    .logRecordBuilder()
                    .setBody("limited")
                    .setAttribute(AttributeKey.stringKey("first"), "abcdefghi")
                    .setAttribute(AttributeKey.stringKey("second"), "123456")
                    .setAttribute(AttributeKey.stringKey("third"), "dropped")
                    .emit();

            LogRecordData record = singleExportedRecord(exporter);
            assertThat(record.getAttributes().asMap()).hasSize(2);
            assertThat(record.getAttributes().get(AttributeKey.stringKey("first"))).isEqualTo("abcde");
            assertThat(record.getAttributes().get(AttributeKey.stringKey("second"))).isEqualTo("12345");
            assertThat(record.getAttributes().get(AttributeKey.stringKey("third"))).isNull();
            assertThat(record.getTotalAttributeCount()).isEqualTo(3);
        } finally {
            loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void processorCanEnrichReadWriteLogRecordBeforeExport() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        LogRecordProcessor enrichingProcessor = new EnrichingProcessor(SimpleLogRecordProcessor.create(exporter));
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(enrichingProcessor)
                .build();

        try {
            loggerProvider.get("processor.logger")
                    .logRecordBuilder()
                    .setBody("original")
                    .emit();

            LogRecordData record = singleExportedRecord(exporter);
            assertThat(record.getBody().asString()).isEqualTo("original");
            assertThat(record.getAttributes().get(PROCESSOR_MARK)).isEqualTo("enriched");
            assertThat(record.getTotalAttributeCount()).isEqualTo(1);
        } finally {
            loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void batchProcessorExportsQueuedRecordsOnForceFlush() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        BatchLogRecordProcessor processor = BatchLogRecordProcessor.builder(exporter)
                .setScheduleDelay(Duration.ofMinutes(5))
                .setExporterTimeout(Duration.ofSeconds(10))
                .setMaxQueueSize(10)
                .setMaxExportBatchSize(2)
                .build();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(processor)
                .build();

        try {
            Logger logger = loggerProvider.get("batch.logger");
            logger.logRecordBuilder().setBody("one").emit();
            logger.logRecordBuilder().setBody("two").emit();
            logger.logRecordBuilder().setBody("three").emit();

            CompletableResultCode flushResult = loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);

            assertThat(flushResult.isSuccess()).isTrue();
            assertThat(exporter.getFinishedLogItems())
                    .extracting(logRecordData -> logRecordData.getBody().asString())
                    .containsExactly("one", "two", "three");
        } finally {
            loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void compositeExporterReceivesExportFlushAndShutdownSignals() {
        RecordingExporter first = new RecordingExporter();
        RecordingExporter second = new RecordingExporter();
        LogRecordExporter compositeExporter = LogRecordExporter.composite(Arrays.asList(first, second));
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setClock(new FixedClock(987_654_321L))
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(compositeExporter))
                .build();

        loggerProvider.get("composite.logger")
                .logRecordBuilder()
                .setAllAttributes(Attributes.of(USER_ID, "bob", RETRY_COUNT, 7L))
                .emit();
        CompletableResultCode flushResult = compositeExporter.flush().join(10, TimeUnit.SECONDS);
        CompletableResultCode providerFlushResult = loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        CompletableResultCode shutdownResult = loggerProvider.shutdown().join(10, TimeUnit.SECONDS);

        assertThat(flushResult.isSuccess()).isTrue();
        assertThat(providerFlushResult.isSuccess()).isTrue();
        assertThat(shutdownResult.isSuccess()).isTrue();
        assertThat(first.getExportedRecords()).hasSize(1);
        assertThat(second.getExportedRecords()).hasSize(1);
        assertThat(first.isFlushed()).isTrue();
        assertThat(second.isFlushed()).isTrue();
        assertThat(first.isShutdown()).isTrue();
        assertThat(second.isShutdown()).isTrue();
        LogRecordData record = first.getExportedRecords().get(0);
        assertThat(record.getEpochNanos()).isEqualTo(987_654_321L);
        assertThat(record.getAttributes().get(USER_ID)).isEqualTo("bob");
        assertThat(record.getAttributes().get(RETRY_COUNT)).isEqualTo(7L);
    }

    @Test
    void shutdownProviderStopsAcceptingNewLogRecordsAndExporterCanReset() {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();
        Logger logger = loggerProvider.get("shutdown.logger");

        logger.logRecordBuilder().setBody("before shutdown").emit();
        assertThat(singleExportedRecord(exporter).getBody().asString()).isEqualTo("before shutdown");

        exporter.reset();
        assertThat(loggerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        logger.logRecordBuilder().setBody("after shutdown").emit();

        assertThat(exporter.getFinishedLogItems()).isEmpty();
    }

    private static LogRecordData singleExportedRecord(InMemoryLogRecordExporter exporter) {
        assertThat(exporter.getFinishedLogItems()).hasSize(1);
        return exporter.getFinishedLogItems().get(0);
    }

    private static final class EnrichingProcessor implements LogRecordProcessor {
        private final LogRecordProcessor delegate;

        private EnrichingProcessor(LogRecordProcessor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onEmit(Context context, ReadWriteLogRecord logRecord) {
            logRecord.setAttribute(PROCESSOR_MARK, "enriched");
            delegate.onEmit(context, logRecord);
        }

        @Override
        public CompletableResultCode shutdown() {
            return delegate.shutdown();
        }

        @Override
        public CompletableResultCode forceFlush() {
            return delegate.forceFlush();
        }
    }

    private static final class RecordingProcessor implements LogRecordProcessor {
        private final List<LogRecordData> records = new ArrayList<>();
        private boolean flushed;
        private boolean shutdown;

        @Override
        public void onEmit(Context context, ReadWriteLogRecord logRecord) {
            records.add(logRecord.toLogRecordData());
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode forceFlush() {
            flushed = true;
            return CompletableResultCode.ofSuccess();
        }

        private List<LogRecordData> getRecords() {
            return Collections.unmodifiableList(records);
        }

        private boolean isFlushed() {
            return flushed;
        }

        private boolean isShutdown() {
            return shutdown;
        }
    }

    private static final class RecordingExporter implements LogRecordExporter {
        private final List<LogRecordData> exportedRecords = new ArrayList<>();
        private boolean flushed;
        private boolean shutdown;

        @Override
        public CompletableResultCode export(Collection<LogRecordData> records) {
            exportedRecords.addAll(records);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            flushed = true;
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }

        private List<LogRecordData> getExportedRecords() {
            return Collections.unmodifiableList(exportedRecords);
        }

        private boolean isFlushed() {
            return flushed;
        }

        private boolean isShutdown() {
            return shutdown;
        }
    }

    private static final class FixedClock implements Clock {
        private final long nowNanos;

        private FixedClock(long nowNanos) {
            this.nowNanos = nowNanos;
        }

        @Override
        public long now() {
            return nowNanos;
        }

        @Override
        public long nanoTime() {
            return nowNanos;
        }
    }
}
