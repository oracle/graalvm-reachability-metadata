/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_log4j_appender_2_17;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.message.StringMapMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class Opentelemetry_log4j_appender_2_17Test {
    private static final AtomicInteger LOGGER_SEQUENCE = new AtomicInteger();

    @AfterEach
    void clearLog4jThreadState() {
        ThreadContext.clearAll();
        MarkerManager.clear();
    }

    @Test
    void builderAppenderExportsFormattedMessagesAndExceptions() {
        String loggerName = newLoggerName("basic");

        try (TestTelemetry telemetry = TestTelemetry.create()) {
            OpenTelemetryAppender appender = OpenTelemetryAppender.builder()
                    .setName("otel-basic-appender")
                    .setOpenTelemetry(telemetry.openTelemetry())
                    .build();

            try (RegisteredAppender ignored = RegisteredAppender.create(loggerName, appender)) {
                Logger logger = LogManager.getLogger(loggerName);
                IllegalStateException failure = new IllegalStateException("database unavailable");

                logger.info("customer {} logged in", "alice");
                logger.error("write failed", failure);

                List<LogRecordData> records = telemetry.records();
                assertThat(records).hasSize(2);

                LogRecordData infoRecord = records.get(0);
                assertThat(infoRecord.getBody().asString()).isEqualTo("customer alice logged in");
                assertThat(infoRecord.getSeverityText()).isEqualTo("INFO");
                assertThat(infoRecord.getInstrumentationScopeInfo().getName())
                        .isEqualTo(loggerName);
                assertThat(infoRecord.getTimestampEpochNanos()).isPositive();

                LogRecordData errorRecord = records.get(1);
                assertThat(errorRecord.getBody().asString()).isEqualTo("write failed");
                assertThat(errorRecord.getSeverityText()).isEqualTo("ERROR");
                assertThat(errorRecord.getAttributes().get(stringKey("exception.type")))
                        .isEqualTo(IllegalStateException.class.getName());
                assertThat(errorRecord.getAttributes().get(stringKey("exception.message")))
                        .isEqualTo("database unavailable");
            }
        }
    }

    @Test
    void optionalCaptureSettingsExportMapMessageMarkerContextThreadAndCodeAttributes() {
        String loggerName = newLoggerName("attributes");

        try (TestTelemetry telemetry = TestTelemetry.create()) {
            OpenTelemetryAppender appender = OpenTelemetryAppender.builder()
                    .setName("otel-attributes-appender")
                    .setOpenTelemetry(telemetry.openTelemetry())
                    .setCaptureMapMessageAttributes(true)
                    .setCaptureMarkerAttribute(true)
                    .setCaptureContextDataAttributes("request.id")
                    .setCaptureExperimentalAttributes(true)
                    .setCaptureCodeAttributes(true)
                    .build();

            try (RegisteredAppender ignored = RegisteredAppender.create(loggerName, appender)) {
                ThreadContext.put("request.id", "req-123");
                ThreadContext.put("not.captured", "ignored");

                StringMapMessage message = new StringMapMessage()
                        .with("message", "checkout accepted")
                        .with("order.id", "A-42")
                        .with("amount", "19.99")
                        .with("otel.event.name", "checkout.completed");

                LogManager.getLogger(loggerName).warn(MarkerManager.getMarker("BUSINESS"), message);

                LogRecordData record = telemetry.singleRecord();
                assertThat(record.getBody().asString()).isEqualTo("checkout accepted");
                assertThat(record.getSeverityText()).isEqualTo("WARN");
                assertThat(record.getEventName()).isEqualTo("checkout.completed");
                assertThat(record.getAttributes().get(stringKey("log4j.map_message.order.id")))
                        .isEqualTo("A-42");
                assertThat(record.getAttributes().get(stringKey("log4j.map_message.amount")))
                        .isEqualTo("19.99");
                assertThat(record.getAttributes().get(stringKey("log4j.map_message.message")))
                        .isNull();
                assertThat(record.getAttributes().get(stringKey("log4j.marker")))
                        .isEqualTo("BUSINESS");
                assertThat(record.getAttributes().get(stringKey("request.id")))
                        .isEqualTo("req-123");
                assertThat(record.getAttributes().get(stringKey("not.captured"))).isNull();
                assertThat(record.getAttributes().get(stringKey("thread.name")))
                        .isEqualTo(Thread.currentThread().getName());
                assertThat(record.getAttributes().get(longKey("thread.id")))
                        .isEqualTo(Thread.currentThread().threadId());
                assertThat(record.getAttributes().asMap().keySet())
                        .extracting(attributeKey -> attributeKey.getKey())
                        .anySatisfy(attributeName -> assertThat(attributeName).startsWith("code."));
            }
        }
    }

    @Test
    void wildcardContextDataCaptureExportsAllThreadContextAttributesAndEventName() {
        String loggerName = newLoggerName("context-data-wildcard");

        try (TestTelemetry telemetry = TestTelemetry.create()) {
            OpenTelemetryAppender appender = OpenTelemetryAppender.builder()
                    .setName("otel-context-data-wildcard-appender")
                    .setOpenTelemetry(telemetry.openTelemetry())
                    .setCaptureContextDataAttributes("*")
                    .build();

            try (RegisteredAppender ignored = RegisteredAppender.create(loggerName, appender)) {
                ThreadContext.put("request.id", "req-456");
                ThreadContext.put("tenant", "acme");
                ThreadContext.put("otel.event.name", "inventory.checked");

                LogManager.getLogger(loggerName).info("checked inventory");

                LogRecordData record = telemetry.singleRecord();
                assertThat(record.getBody().asString()).isEqualTo("checked inventory");
                assertThat(record.getEventName()).isEqualTo("inventory.checked");
                assertThat(record.getAttributes().get(stringKey("request.id")))
                        .isEqualTo("req-456");
                assertThat(record.getAttributes().get(stringKey("tenant"))).isEqualTo("acme");
                assertThat(record.getAttributes().get(stringKey("otel.event.name"))).isNull();
            }
        }
    }

    @Test
    void installReplaysEventsLoggedBeforeOpenTelemetryWasAvailable() {
        String loggerName = newLoggerName("replay");

        try (TestTelemetry telemetry = TestTelemetry.create()) {
            OpenTelemetryAppender appender = OpenTelemetryAppender.builder()
                    .setName("otel-replay-appender")
                    .setNumLogsCapturedBeforeOtelInstall(2)
                    .build();

            try (RegisteredAppender ignored = RegisteredAppender.create(loggerName, appender)) {
                Logger logger = LogManager.getLogger(loggerName);
                logger.info("first buffered log");
                logger.warn("second buffered log");

                assertThat(telemetry.records()).isEmpty();

                OpenTelemetryAppender.install(telemetry.openTelemetry());
                assertThat(telemetry.records())
                        .extracting(record -> record.getBody().asString())
                        .containsExactly("first buffered log", "second buffered log");

                logger.error("after installation");
                assertThat(telemetry.records())
                        .extracting(record -> record.getBody().asString())
                        .containsExactly(
                                "first buffered log", "second buffered log", "after installation");
            }
        }
    }

    @Test
    void emittedLogRecordUsesTheCurrentOpenTelemetryContext() {
        String loggerName = newLoggerName("span-context");

        try (TestTelemetry telemetry = TestTelemetry.create()) {
            OpenTelemetryAppender appender = OpenTelemetryAppender.builder()
                    .setName("otel-context-appender")
                    .setOpenTelemetry(telemetry.openTelemetry())
                    .build();

            try (RegisteredAppender ignored = RegisteredAppender.create(loggerName, appender)) {
                Span span = telemetry.openTelemetry()
                        .getTracer("log4j-test-tracer")
                        .spanBuilder("request-span")
                        .startSpan();
                try (Scope ignoredScope = span.makeCurrent()) {
                    LogManager.getLogger(loggerName).info("inside traced request");
                } finally {
                    span.end();
                }

                LogRecordData record = telemetry.singleRecord();
                assertThat(record.getBody().asString()).isEqualTo("inside traced request");
                assertThat(record.getSpanContext().isValid()).isTrue();
                assertThat(record.getSpanContext().getTraceId())
                        .isEqualTo(span.getSpanContext().getTraceId());
                assertThat(record.getSpanContext().getSpanId())
                        .isEqualTo(span.getSpanContext().getSpanId());
            }
        }
    }

    private static String newLoggerName(String testName) {
        return Opentelemetry_log4j_appender_2_17Test.class.getName()
                + "."
                + testName
                + "."
                + LOGGER_SEQUENCE.incrementAndGet();
    }

    private static final class RegisteredAppender implements AutoCloseable {
        private final LoggerContext loggerContext;
        private final String loggerName;
        private final OpenTelemetryAppender appender;

        private RegisteredAppender(
                LoggerContext loggerContext, String loggerName, OpenTelemetryAppender appender) {
            this.loggerContext = loggerContext;
            this.loggerName = loggerName;
            this.appender = appender;
        }

        private static RegisteredAppender create(
                String loggerName, OpenTelemetryAppender appender) {
            LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = loggerContext.getConfiguration();
            appender.start();
            configuration.addAppender(appender);

            LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                    .withLoggerName(loggerName)
                    .withLevel(Level.ALL)
                    .withAdditivity(false)
                    .withRefs(new AppenderRef[0])
                    .withIncludeLocation("true")
                    .withConfig(configuration)
                    .build();
            loggerConfig.addAppender(appender, Level.ALL, null);
            configuration.addLogger(loggerName, loggerConfig);
            loggerContext.updateLoggers();
            return new RegisteredAppender(loggerContext, loggerName, appender);
        }

        @Override
        public void close() {
            Configuration configuration = loggerContext.getConfiguration();
            LoggerConfig loggerConfig = configuration.getLoggerConfig(loggerName);
            loggerConfig.removeAppender(appender.getName());
            configuration.removeLogger(loggerName);
            configuration.getAppenders().remove(appender.getName());
            loggerContext.updateLoggers();
            appender.stop();
            ThreadContext.clearAll();
        }
    }

    private static final class TestTelemetry implements AutoCloseable {
        private final InMemoryLogRecordExporter exporter;
        private final OpenTelemetrySdk openTelemetry;

        private TestTelemetry(InMemoryLogRecordExporter exporter, OpenTelemetrySdk openTelemetry) {
            this.exporter = exporter;
            this.openTelemetry = openTelemetry;
        }

        private static TestTelemetry create() {
            InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
            SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                    .build();
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
            OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                    .setLoggerProvider(loggerProvider)
                    .setTracerProvider(tracerProvider)
                    .build();
            return new TestTelemetry(exporter, openTelemetry);
        }

        private OpenTelemetrySdk openTelemetry() {
            return openTelemetry;
        }

        private List<LogRecordData> records() {
            assertThat(openTelemetry.getSdkLoggerProvider()
                            .forceFlush()
                            .join(10, TimeUnit.SECONDS)
                            .isSuccess())
                    .isTrue();
            return exporter.getFinishedLogRecordItems();
        }

        private LogRecordData singleRecord() {
            List<LogRecordData> records = records();
            assertThat(records).hasSize(1);
            return records.get(0);
        }

        @Override
        public void close() {
            assertThat(openTelemetry.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        }
    }
}
