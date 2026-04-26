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
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class Opentelemetry_api_logsTest {
    private static final ContextKey<String> TENANT_KEY = ContextKey.named("tenant");
    private static final AttributeKey<String> TENANT_ATTRIBUTE = AttributeKey.stringKey("tenant");
    private static final AttributeKey<Long> ATTEMPT_ATTRIBUTE = AttributeKey.longKey("attempt");
    private static final AttributeKey<List<String>> TAGS_ATTRIBUTE = AttributeKey.stringArrayKey("tags");

    @AfterEach
    void resetGlobalLoggerProvider() {
        GlobalLoggerProvider.resetForTest();
    }

    @Test
    void noopLoggersSupportAllBuilderOperations() {
        LoggerProvider provider = LoggerProvider.noop();
        Logger logger = provider.loggerBuilder("io.opentelemetry.example.logs")
                .setEventDomain("audit")
                .setInstrumentationVersion("1.19.0-alpha")
                .setSchemaUrl("https://opentelemetry.io/schemas/1.19.0")
                .build();

        assertThat(logger).isNotNull();

        LogRecordBuilder logRecordBuilder = logger.logRecordBuilder();
        Context context = Context.root().with(TENANT_KEY, "tenant-a");
        Attributes attributes = Attributes.of(
                TENANT_ATTRIBUTE, "tenant-a",
                ATTEMPT_ATTRIBUTE, 3L,
                TAGS_ATTRIBUTE, List.of("cold-start", "native")
        );

        assertThat(logRecordBuilder.setAllAttributes(attributes)).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setAllAttributes(Attributes.empty())).isSameAs(logRecordBuilder);
        assertThat(logRecordBuilder.setAllAttributes(null)).isSameAs(logRecordBuilder);

        logRecordBuilder
                .setEpoch(1_700_000_000L, TimeUnit.MILLISECONDS)
                .setEpoch(Instant.parse("2024-01-02T03:04:05Z"))
                .setContext(context)
                .setSeverity(Severity.ERROR3)
                .setSeverityText("ERROR3")
                .setBody("database unavailable")
                .setAttribute(TENANT_ATTRIBUTE, "tenant-a")
                .setAttribute(ATTEMPT_ATTRIBUTE, 3L)
                .emit();

        LogRecordBuilder eventRecordBuilder = logger.eventBuilder("startup");
        assertThat(eventRecordBuilder).isNotNull();
        eventRecordBuilder
                .setSeverity(Severity.INFO)
                .setSeverityText("INFO")
                .setBody("service started")
                .setAttribute(TAGS_ATTRIBUTE, List.of("startup", "ready"))
                .emit();

        Logger defaultLogger = provider.get("io.opentelemetry.example.default");
        assertThat(defaultLogger).isNotNull();
        defaultLogger.logRecordBuilder().setBody("from default get").emit();
    }

    @Test
    void defaultInterfaceMethodsDelegateToBuildersAndExpandAttributes() {
        RecordingLoggerProvider provider = new RecordingLoggerProvider();

        Logger logger = provider.get("io.opentelemetry.example.recording");
        assertThat(provider.builders).hasSize(1);
        assertThat(provider.builders.get(0).instrumentationName).isEqualTo("io.opentelemetry.example.recording");
        assertThat(provider.builtLoggers).hasSize(1);

        LogRecordBuilder recordBuilder = logger.logRecordBuilder();
        Attributes attributes = Attributes.of(
                TENANT_ATTRIBUTE, "tenant-b",
                ATTEMPT_ATTRIBUTE, 7L,
                TAGS_ATTRIBUTE, List.of("reload", "warm")
        );

        assertThat(recordBuilder.setAllAttributes(attributes)).isSameAs(recordBuilder);
        assertThat(recordBuilder.setAllAttributes(Attributes.empty())).isSameAs(recordBuilder);
        assertThat(recordBuilder.setAllAttributes(null)).isSameAs(recordBuilder);

        recordBuilder
                .setSeverity(Severity.INFO2)
                .setBody("configuration reloaded")
                .emit();

        RecordingLogger recordingLogger = provider.builtLoggers.get(0);
        assertThat(recordingLogger.recordBuilders).hasSize(1);
        RecordingLogRecordBuilder recordingBuilder = recordingLogger.recordBuilders.get(0);
        assertThat(recordingBuilder.eventName).isNull();
        assertThat(recordingBuilder.attributes)
                .containsEntry("tenant", "tenant-b")
                .containsEntry("attempt", 7L)
                .containsEntry("tags", List.of("reload", "warm"));
        assertThat(recordingBuilder.severity).isSameAs(Severity.INFO2);
        assertThat(recordingBuilder.body).isEqualTo("configuration reloaded");
        assertThat(recordingBuilder.emittedCount).isEqualTo(1);

        EventBuilder eventBuilder = logger.eventBuilder("cache-evicted");
        eventBuilder.setSeverity(Severity.WARN).setBody("user cache evicted").emit();

        assertThat(recordingLogger.recordBuilders).hasSize(2);
        assertThat(recordingLogger.recordBuilders.get(1).eventName).isEqualTo("cache-evicted");
        assertThat(recordingLogger.recordBuilders.get(1).severity).isSameAs(Severity.WARN);
        assertThat(recordingLogger.recordBuilders.get(1).body).isEqualTo("user cache evicted");
    }

    @Test
    void globalLoggerProviderPublishesConfiguredProviderAndRejectsSecondRegistration() {
        RecordingLoggerProvider firstProvider = new RecordingLoggerProvider();
        GlobalLoggerProvider.set(firstProvider);

        assertThat(GlobalLoggerProvider.get()).isSameAs(firstProvider);

        Logger logger = GlobalLoggerProvider.get()
                .loggerBuilder("io.opentelemetry.example.global")
                .setEventDomain("audit")
                .setSchemaUrl("https://opentelemetry.io/schemas/1.19.0")
                .setInstrumentationVersion("1.19.0-alpha")
                .build();

        logger.eventBuilder("rotation")
                .setSeverity(Severity.WARN2)
                .setBody("log file rotated")
                .emit();

        assertThat(firstProvider.builders).hasSize(1);
        RecordingLoggerBuilder configuredBuilder = firstProvider.builders.get(0);
        assertThat(configuredBuilder.instrumentationName).isEqualTo("io.opentelemetry.example.global");
        assertThat(configuredBuilder.eventDomain).isEqualTo("audit");
        assertThat(configuredBuilder.schemaUrl).isEqualTo("https://opentelemetry.io/schemas/1.19.0");
        assertThat(configuredBuilder.instrumentationVersion).isEqualTo("1.19.0-alpha");

        assertThat(firstProvider.builtLoggers).hasSize(1);
        RecordingLogger configuredLogger = firstProvider.builtLoggers.get(0);
        assertThat(configuredLogger.recordBuilders).hasSize(1);
        assertThat(configuredLogger.recordBuilders.get(0).eventName).isEqualTo("rotation");
        assertThat(configuredLogger.recordBuilders.get(0).severity).isSameAs(Severity.WARN2);
        assertThat(configuredLogger.recordBuilders.get(0).body).isEqualTo("log file rotated");

        RecordingLoggerProvider secondProvider = new RecordingLoggerProvider();
        assertThatThrownBy(() -> GlobalLoggerProvider.set(secondProvider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GlobalLoggerProvider.set has already been called");

        GlobalLoggerProvider.resetForTest();
        GlobalLoggerProvider.set(secondProvider);
        assertThat(GlobalLoggerProvider.get()).isSameAs(secondProvider);
    }

    @Test
    void noopLoggerWithoutEventDomainLogsApiUsageWarningWhenCreatingEventBuilder() {
        java.util.logging.Logger apiUsageLogger = java.util.logging.Logger.getLogger("io.opentelemetry.ApiUsageLogging");
        Level previousLevel = apiUsageLogger.getLevel();
        boolean previousUseParentHandlers = apiUsageLogger.getUseParentHandlers();
        RecordingLogHandler handler = new RecordingLogHandler();
        handler.setLevel(Level.ALL);
        apiUsageLogger.addHandler(handler);
        apiUsageLogger.setUseParentHandlers(false);
        apiUsageLogger.setLevel(Level.ALL);
        try {
            Logger logger = LoggerProvider.noop().get("io.opentelemetry.example.no-domain");

            EventBuilder eventBuilder = logger.eventBuilder("startup");
            assertThat(eventBuilder).isNotNull();

            eventBuilder.setBody("service started").emit();

            assertThat(handler.records).hasSize(1);
            LogRecord warning = handler.records.get(0);
            assertThat(warning.getLevel()).isEqualTo(Level.WARNING);
            assertThat(warning.getMessage())
                    .contains("Cannot emit event from Logger without event domain")
                    .contains("LoggerBuilder#setEventDomain(String)");
        } finally {
            apiUsageLogger.removeHandler(handler);
            apiUsageLogger.setUseParentHandlers(previousUseParentHandlers);
            apiUsageLogger.setLevel(previousLevel);
        }
    }

    @Test
    void noopLoggerWithEventDomainDoesNotLogApiUsageWarningWhenCreatingEventBuilder() {
        java.util.logging.Logger apiUsageLogger = java.util.logging.Logger.getLogger("io.opentelemetry.ApiUsageLogging");
        Level previousLevel = apiUsageLogger.getLevel();
        boolean previousUseParentHandlers = apiUsageLogger.getUseParentHandlers();
        RecordingLogHandler handler = new RecordingLogHandler();
        handler.setLevel(Level.ALL);
        apiUsageLogger.addHandler(handler);
        apiUsageLogger.setUseParentHandlers(false);
        apiUsageLogger.setLevel(Level.ALL);
        try {
            Logger logger = LoggerProvider.noop()
                    .loggerBuilder("io.opentelemetry.example.with-domain")
                    .setEventDomain("audit")
                    .build();

            EventBuilder eventBuilder = logger.eventBuilder("startup");
            assertThat(eventBuilder).isNotNull();

            eventBuilder.setBody("service started").emit();

            assertThat(handler.records).isEmpty();
        } finally {
            apiUsageLogger.removeHandler(handler);
            apiUsageLogger.setUseParentHandlers(previousUseParentHandlers);
            apiUsageLogger.setLevel(previousLevel);
        }
    }

    @Test
    void severitiesExposeStableNumbersAndLookup() {
        assertThat(Severity.values()).containsExactly(
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
                Severity.FATAL4
        );
        assertThat(Severity.values())
                .extracting(Severity::getSeverityNumber)
                .containsExactly(
                        0, 1, 2, 3, 4,
                        5, 6, 7, 8,
                        9, 10, 11, 12,
                        13, 14, 15, 16,
                        17, 18, 19, 20,
                        21, 22, 23, 24
                );
        assertThat(Severity.valueOf("WARN3")).isSameAs(Severity.WARN3);
        assertThat(Severity.ERROR.getSeverityNumber()).isLessThan(Severity.FATAL.getSeverityNumber());
    }

    private static final class RecordingLoggerProvider implements LoggerProvider {
        private final List<RecordingLoggerBuilder> builders = new ArrayList<>();
        private final List<RecordingLogger> builtLoggers = new ArrayList<>();

        @Override
        public LoggerBuilder loggerBuilder(String instrumentationName) {
            RecordingLoggerBuilder builder = new RecordingLoggerBuilder(this, instrumentationName);
            builders.add(builder);
            return builder;
        }
    }

    private static final class RecordingLoggerBuilder implements LoggerBuilder {
        private final RecordingLoggerProvider owner;
        private final String instrumentationName;
        private String eventDomain;
        private String schemaUrl;
        private String instrumentationVersion;

        private RecordingLoggerBuilder(RecordingLoggerProvider owner, String instrumentationName) {
            this.owner = owner;
            this.instrumentationName = instrumentationName;
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
            RecordingLogger logger = new RecordingLogger(
                    instrumentationName,
                    eventDomain,
                    schemaUrl,
                    instrumentationVersion
            );
            owner.builtLoggers.add(logger);
            return logger;
        }
    }

    private static final class RecordingLogger implements Logger {
        private final String instrumentationName;
        private final String eventDomain;
        private final String schemaUrl;
        private final String instrumentationVersion;
        private final List<RecordingLogRecordBuilder> recordBuilders = new ArrayList<>();

        private RecordingLogger(
                String instrumentationName,
                String eventDomain,
                String schemaUrl,
                String instrumentationVersion
        ) {
            this.instrumentationName = instrumentationName;
            this.eventDomain = eventDomain;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
        }

        @Override
        public EventBuilder eventBuilder(String eventName) {
            RecordingLogRecordBuilder builder = new RecordingLogRecordBuilder(
                    instrumentationName,
                    eventDomain,
                    schemaUrl,
                    instrumentationVersion,
                    eventName
            );
            recordBuilders.add(builder);
            return builder;
        }

        @Override
        public LogRecordBuilder logRecordBuilder() {
            RecordingLogRecordBuilder builder = new RecordingLogRecordBuilder(
                    instrumentationName,
                    eventDomain,
                    schemaUrl,
                    instrumentationVersion,
                    null
            );
            recordBuilders.add(builder);
            return builder;
        }
    }

    private static final class RecordingLogHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingLogRecordBuilder implements EventBuilder {
        private final String instrumentationName;
        private final String eventDomain;
        private final String schemaUrl;
        private final String instrumentationVersion;
        private final String eventName;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private long epoch;
        private TimeUnit epochUnit;
        private Instant instant;
        private Context context;
        private Severity severity;
        private String severityText;
        private String body;
        private int emittedCount;

        private RecordingLogRecordBuilder(
                String instrumentationName,
                String eventDomain,
                String schemaUrl,
                String instrumentationVersion,
                String eventName
        ) {
            this.instrumentationName = instrumentationName;
            this.eventDomain = eventDomain;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
            this.eventName = eventName;
        }

        @Override
        public LogRecordBuilder setEpoch(long epoch, TimeUnit unit) {
            this.epoch = epoch;
            this.epochUnit = unit;
            return this;
        }

        @Override
        public LogRecordBuilder setEpoch(Instant instant) {
            this.instant = instant;
            return this;
        }

        @Override
        public LogRecordBuilder setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        public LogRecordBuilder setSeverity(Severity severity) {
            this.severity = severity;
            return this;
        }

        @Override
        public LogRecordBuilder setSeverityText(String severityText) {
            this.severityText = severityText;
            return this;
        }

        @Override
        public LogRecordBuilder setBody(String body) {
            this.body = body;
            return this;
        }

        @Override
        public <T> LogRecordBuilder setAttribute(AttributeKey<T> key, T value) {
            attributes.put(key.getKey(), value);
            return this;
        }

        @Override
        public void emit() {
            emittedCount++;
        }
    }
}
