/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_api_logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.EventBuilder;
import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Opentelemetry_api_logsTest {
    private static final AttributeKey<String> COMPONENT_KEY = AttributeKey.stringKey("component");
    private static final AttributeKey<Long> ATTEMPT_KEY = AttributeKey.longKey("attempt");
    private static final AttributeKey<Boolean> RETRYABLE_KEY = AttributeKey.booleanKey("retryable");

    @BeforeEach
    void resetGlobalLoggerProviderBeforeTest() {
        GlobalLoggerProvider.resetForTest();
    }

    @AfterEach
    void resetGlobalLoggerProviderAfterTest() {
        GlobalLoggerProvider.resetForTest();
    }

    @Test
    void noopProviderAcceptsCompleteLogAndEventConfiguration() {
        LoggerProvider provider = LoggerProvider.noop();
        Logger logger = provider.loggerBuilder("noop-instrumentation")
                .setEventDomain("audit")
                .setSchemaUrl("https://opentelemetry.io/schemas/1.19.0")
                .setInstrumentationVersion("1.2.3")
                .build();

        LogRecordBuilder logRecordBuilder = logger.logRecordBuilder();
        assertThat(logRecordBuilder.setEpoch(Instant.parse("2022-10-01T12:34:56.123456789Z")))
                .isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setEpoch(42, TimeUnit.MILLISECONDS)).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setContext(Context.current())).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setSeverity(Severity.WARN3)).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setSeverityText("WARN")).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setBody("ignored by noop logger")).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setAllAttributes(Attributes.of(COMPONENT_KEY, "noop", ATTEMPT_KEY, 1L)))
                .isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setAttribute(RETRYABLE_KEY, false)).isSameAs(logRecordBuilder);
        logRecordBuilder.emit();

        EventBuilder eventBuilder = logger.eventBuilder("process.started");
        assertThat(eventBuilder.setSeverity(Severity.INFO)).isSameAs(eventBuilder);
        assertThat(eventBuilder.setBody("event body")).isSameAs(eventBuilder);
        assertThat(eventBuilder.setAttribute(COMPONENT_KEY, "events")).isSameAs(eventBuilder);
        eventBuilder.emit();
    }

    @Test
    void loggerProviderDefaultGetBuildsLoggerForInstrumentationName() {
        RecordingLoggerProvider provider = new RecordingLoggerProvider();

        Logger logger = provider.get("checkout-service");

        assertThat(logger).isInstanceOf(RecordingLogger.class);
        assertThat(provider.builders).hasSize(1);
        assertThat(provider.builders.get(0).instrumentationName).isEqualTo("checkout-service");
        assertThat(provider.builders.get(0).built).isTrue();
    }

    @Test
    void loggerBuilderConfigurationAndLogRecordBuilderCaptureEmittedRecords() {
        RecordingLoggerProvider provider = new RecordingLoggerProvider();
        RecordingLogger logger = (RecordingLogger) provider.loggerBuilder("payments")
                .setEventDomain("business-events")
                .setSchemaUrl("https://opentelemetry.io/schemas/1.19.0")
                .setInstrumentationVersion("2.5.0")
                .build();
        ContextKey<String> requestKey = ContextKey.named("request-id");
        Context context = Context.root().with(requestKey, "request-123");
        Instant timestamp = Instant.parse("2022-10-01T12:34:56.123456789Z");

        LogRecordBuilder builder = logger.logRecordBuilder()
                .setEpoch(timestamp)
                .setContext(context)
                .setSeverity(Severity.ERROR3)
                .setSeverityText("ERROR")
                .setBody("payment failed")
                .setAllAttributes(Attributes.of(COMPONENT_KEY, "payments", ATTEMPT_KEY, 3L));
        builder.setAttribute(RETRYABLE_KEY, true).emit();

        assertThat(logger.configuration.instrumentationName).isEqualTo("payments");
        assertThat(logger.configuration.eventDomain).isEqualTo("business-events");
        assertThat(logger.configuration.schemaUrl).isEqualTo("https://opentelemetry.io/schemas/1.19.0");
        assertThat(logger.configuration.instrumentationVersion).isEqualTo("2.5.0");
        assertThat(logger.records).hasSize(1);
        RecordingLogRecord record = logger.records.get(0);
        assertThat(record.eventName).isNull();
        assertThat(record.epochNanos).isEqualTo(1_664_627_696_123_456_789L);
        assertThat(record.context).isSameAs(context);
        assertThat(record.severity).isEqualTo(Severity.ERROR3);
        assertThat(record.severityText).isEqualTo("ERROR");
        assertThat(record.body).isEqualTo("payment failed");
        assertThat(record.attributes)
                .containsEntry(COMPONENT_KEY, "payments")
                .containsEntry(ATTEMPT_KEY, 3L)
                .containsEntry(RETRYABLE_KEY, true);
    }

    @Test
    void eventBuilderCapturesEventNameAndUsesTimeUnitEpochs() {
        RecordingLogger logger = new RecordingLogger(new RecordingLoggerConfiguration("orders"));

        logger.eventBuilder("order.created")
                .setEpoch(123, TimeUnit.MILLISECONDS)
                .setSeverity(Severity.INFO2)
                .setSeverityText("INFO")
                .setBody("order event")
                .setAttribute(COMPONENT_KEY, "orders")
                .emit();

        assertThat(logger.records).hasSize(1);
        RecordingLogRecord event = logger.records.get(0);
        assertThat(event.eventName).isEqualTo("order.created");
        assertThat(event.epochNanos).isEqualTo(123_000_000L);
        assertThat(event.severity).isEqualTo(Severity.INFO2);
        assertThat(event.severityText).isEqualTo("INFO");
        assertThat(event.body).isEqualTo("order event");
        assertThat(event.attributes).containsEntry(COMPONENT_KEY, "orders");
    }

    @Test
    void setAllAttributesLeavesExistingAttributesUntouchedWhenInputIsNullOrEmpty() {
        RecordingLogger logger = new RecordingLogger(new RecordingLoggerConfiguration("inventory"));

        LogRecordBuilder builder = logger.logRecordBuilder().setAttribute(COMPONENT_KEY, "inventory");
        assertThat(builder.setAllAttributes(null)).isSameAs(builder);
        assertThat(builder.setAllAttributes(Attributes.empty())).isSameAs(builder);
        builder.emit();

        assertThat(logger.records).hasSize(1);
        RecordingLogRecord record = logger.records.get(0);
        assertThat(record.attributes).hasSize(1).containsEntry(COMPONENT_KEY, "inventory");
    }

    @Test
    void globalLoggerProviderCanBeInstalledUsedAndReset() {
        RecordingLoggerProvider provider = new RecordingLoggerProvider();

        GlobalLoggerProvider.set(provider);
        Logger logger = GlobalLoggerProvider.get().get("global-component");
        logger.logRecordBuilder().setSeverity(Severity.DEBUG).setBody("global message").emit();

        assertThat(GlobalLoggerProvider.get()).isSameAs(provider);
        RecordingLogger recordingLogger = provider.loggers.get(0);
        assertThat(recordingLogger.configuration.instrumentationName).isEqualTo("global-component");
        assertThat(recordingLogger.records).hasSize(1);
        assertThat(recordingLogger.records.get(0).severity).isEqualTo(Severity.DEBUG);
        assertThat(recordingLogger.records.get(0).body).isEqualTo("global message");

        GlobalLoggerProvider.resetForTest();
        assertThat(GlobalLoggerProvider.get()).isNotSameAs(provider);
    }

    @Test
    void globalLoggerProviderRejectsSecondProviderUntilReset() {
        RecordingLoggerProvider firstProvider = new RecordingLoggerProvider();
        RecordingLoggerProvider secondProvider = new RecordingLoggerProvider();

        GlobalLoggerProvider.set(firstProvider);

        assertThatThrownBy(() -> GlobalLoggerProvider.set(secondProvider)).isInstanceOf(IllegalStateException.class);
        assertThat(GlobalLoggerProvider.get()).isSameAs(firstProvider);

        GlobalLoggerProvider.resetForTest();
        GlobalLoggerProvider.set(secondProvider);

        assertThat(GlobalLoggerProvider.get()).isSameAs(secondProvider);
    }

    @Test
    void severityNumbersCoverOpenTelemetryLogSeverityRange() {
        assertThat(Severity.values())
                .containsExactly(
                        Severity.UNDEFINED_SEVERITY_NUMBER,
                        Severity.TRACE,
                        Severity.TRACE2,
                        Severity.TRACE3,
                        Severity.TRACE4,
                        Severity.DEBUG,
                        Severity.DEBUG2,
                        Severity.DEBUG3,
                        Severity.DEBUG4,
                        Severity.INFO,
                        Severity.INFO2,
                        Severity.INFO3,
                        Severity.INFO4,
                        Severity.WARN,
                        Severity.WARN2,
                        Severity.WARN3,
                        Severity.WARN4,
                        Severity.ERROR,
                        Severity.ERROR2,
                        Severity.ERROR3,
                        Severity.ERROR4,
                        Severity.FATAL,
                        Severity.FATAL2,
                        Severity.FATAL3,
                        Severity.FATAL4);
        assertThat(Arrays.stream(Severity.values()).mapToInt(Severity::getSeverityNumber).toArray())
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
                        18, 19, 20, 21, 22, 23, 24);
        assertThat(Severity.valueOf("FATAL4")).isEqualTo(Severity.FATAL4);
    }

    private static final class RecordingLoggerProvider implements LoggerProvider {
        private final List<RecordingLoggerBuilder> builders = new ArrayList<>();
        private final List<RecordingLogger> loggers = new ArrayList<>();

        @Override
        public LoggerBuilder loggerBuilder(String instrumentationName) {
            RecordingLoggerBuilder builder = new RecordingLoggerBuilder(instrumentationName, loggers);
            builders.add(builder);
            return builder;
        }
    }

    private static final class RecordingLoggerBuilder implements LoggerBuilder {
        private final String instrumentationName;
        private final List<RecordingLogger> loggers;
        private String eventDomain;
        private String schemaUrl;
        private String instrumentationVersion;
        private boolean built;

        private RecordingLoggerBuilder(String instrumentationName, List<RecordingLogger> loggers) {
            this.instrumentationName = instrumentationName;
            this.loggers = loggers;
        }

        @Override
        public LoggerBuilder setEventDomain(String eventDomain) {
            this.eventDomain = eventDomain;
            return this;
        }

        @Override
        public LoggerBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public LoggerBuilder setInstrumentationVersion(String instrumentationVersion) {
            this.instrumentationVersion = instrumentationVersion;
            return this;
        }

        @Override
        public Logger build() {
            built = true;
            RecordingLogger logger = new RecordingLogger(new RecordingLoggerConfiguration(
                    instrumentationName, eventDomain, schemaUrl, instrumentationVersion));
            loggers.add(logger);
            return logger;
        }
    }

    private static final class RecordingLogger implements Logger {
        private final RecordingLoggerConfiguration configuration;
        private final List<RecordingLogRecord> records = new ArrayList<>();

        private RecordingLogger(RecordingLoggerConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public EventBuilder eventBuilder(String eventName) {
            return new RecordingLogRecordBuilder(records, eventName);
        }

        @Override
        public LogRecordBuilder logRecordBuilder() {
            return new RecordingLogRecordBuilder(records, null);
        }
    }

    private static final class RecordingLoggerConfiguration {
        private final String instrumentationName;
        private final String eventDomain;
        private final String schemaUrl;
        private final String instrumentationVersion;

        private RecordingLoggerConfiguration(String instrumentationName) {
            this(instrumentationName, null, null, null);
        }

        private RecordingLoggerConfiguration(
                String instrumentationName, String eventDomain, String schemaUrl, String instrumentationVersion) {
            this.instrumentationName = instrumentationName;
            this.eventDomain = eventDomain;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
        }
    }

    private static final class RecordingLogRecordBuilder implements EventBuilder {
        private final List<RecordingLogRecord> records;
        private final RecordingLogRecord record;

        private RecordingLogRecordBuilder(List<RecordingLogRecord> records, String eventName) {
            this.records = records;
            this.record = new RecordingLogRecord(eventName);
        }

        @Override
        public LogRecordBuilder setEpoch(long timestamp, TimeUnit unit) {
            record.epochNanos = unit.toNanos(timestamp);
            return this;
        }

        @Override
        public LogRecordBuilder setEpoch(Instant instant) {
            record.epochNanos = TimeUnit.SECONDS.toNanos(instant.getEpochSecond()) + instant.getNano();
            return this;
        }

        @Override
        public LogRecordBuilder setContext(Context context) {
            record.context = context;
            return this;
        }

        @Override
        public LogRecordBuilder setSeverity(Severity severity) {
            record.severity = severity;
            return this;
        }

        @Override
        public LogRecordBuilder setSeverityText(String severityText) {
            record.severityText = severityText;
            return this;
        }

        @Override
        public LogRecordBuilder setBody(String body) {
            record.body = body;
            return this;
        }

        @Override
        public <T> LogRecordBuilder setAttribute(AttributeKey<T> key, T value) {
            record.attributes.put(key, value);
            return this;
        }

        @Override
        public void emit() {
            records.add(record);
        }
    }

    private static final class RecordingLogRecord {
        private final String eventName;
        private final Map<AttributeKey<?>, Object> attributes = new LinkedHashMap<>();
        private Long epochNanos;
        private Context context;
        private Severity severity;
        private String severityText;
        private String body;

        private RecordingLogRecord(String eventName) {
            this.eventName = eventName;
        }
    }
}
