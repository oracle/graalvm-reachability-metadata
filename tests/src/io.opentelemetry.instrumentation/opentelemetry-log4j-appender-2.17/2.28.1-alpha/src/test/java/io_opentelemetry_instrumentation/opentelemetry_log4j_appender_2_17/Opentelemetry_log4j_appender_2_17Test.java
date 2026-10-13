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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.Test;

public class Opentelemetry_log4j_appender_2_17Test {
    @Test
    void emitsLogRecordWithMessageSeverityAndException() {
        try (TestTelemetry telemetry = TestTelemetry.create()) {
            OpenTelemetryAppender appender = newAppender(telemetry.openTelemetry);
            IllegalStateException exception = new IllegalStateException("payment unavailable");

            appender.append(event(
                    new SimpleMessage("payment failed"),
                    Level.ERROR,
                    null,
                    exception,
                    new SortedArrayStringMap(),
                    null));

            LogRecordData record = telemetry.singleRecord();
            assertThat(record.getBody().asString()).isEqualTo("payment failed");
            assertThat(record.getSeverity()).isEqualTo(Severity.ERROR);
            assertThat(record.getSeverityText()).isEqualTo("ERROR");
            assertThat(record.getAttributes().get(stringKey("exception.type")))
                    .isEqualTo(IllegalStateException.class.getName());
            assertThat(record.getAttributes().get(stringKey("exception.message")))
                    .isEqualTo("payment unavailable");
        }
    }

    @Test
    void associatesLogRecordWithCurrentSpan() {
        SpanContext spanContext = SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        try (TestTelemetry telemetry = TestTelemetry.create();
                Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            OpenTelemetryAppender appender = newAppender(telemetry.openTelemetry);

            appender.append(event(
                    new SimpleMessage("trace-correlated log"),
                    Level.INFO,
                    null,
                    null,
                    new SortedArrayStringMap(),
                    null));

            assertThat(telemetry.singleRecord().getSpanContext()).isEqualTo(spanContext);
        }
    }

    @Test
    void capturesConfiguredLog4jAttributes() {
        try (TestTelemetry telemetry = TestTelemetry.create()) {
            StringMap contextData = new SortedArrayStringMap();
            contextData.putValue("request.id", "request-123");
            contextData.putValue("tenant", "acme");
            contextData.putValue("ignored", "not-captured");
            Marker marker = MarkerManager.getMarker("AUDIT");
            StringMapMessage message = new StringMapMessage();
            message.put("message", "order accepted");
            message.put("order.id", "A123");
            message.put("otel.event.name", "order.accepted");
            OpenTelemetryAppender appender = OpenTelemetryAppender.builder()
                    .setName("configured-appender")
                    .setOpenTelemetry(telemetry.openTelemetry)
                    .setCaptureExperimentalAttributes(true)
                    .setCaptureCodeAttributes(true)
                    .setCaptureMapMessageAttributes(true)
                    .setCaptureMarkerAttribute(true)
                    .setCaptureContextDataAttributes("request.id, tenant")
                    .build();

            appender.append(event(
                    message,
                    Level.INFO,
                    marker,
                    null,
                    contextData,
                    new StackTraceElement(
                            "example.OrderService", "submit", "OrderService.java", 42)));

            LogRecordData record = telemetry.singleRecord();
            assertThat(record.getBody().asString()).isEqualTo("order accepted");
            assertThat(record.getEventName()).isEqualTo("order.accepted");
            assertThat(record.getAttributes().get(stringKey("order.id"))).isNull();
            assertThat(record.getAttributes().get(stringKey("log4j.map_message.order.id")))
                    .isEqualTo("A123");
            assertThat(record.getAttributes().get(stringKey("log4j.marker"))).isEqualTo("AUDIT");
            assertThat(record.getAttributes().get(stringKey("request.id")))
                    .isEqualTo("request-123");
            assertThat(record.getAttributes().get(stringKey("tenant"))).isEqualTo("acme");
            assertThat(record.getAttributes().get(stringKey("ignored"))).isNull();
            assertThat(record.getAttributes().get(stringKey("thread.name")))
                    .isEqualTo("test-thread");
            assertThat(record.getAttributes().get(longKey("thread.id"))).isEqualTo(17L);
            assertThat(codeFilePath(record)).isEqualTo("OrderService.java");
            assertThat(codeLineNumber(record)).isEqualTo(42L);
        }
    }

    @Test
    void capturesAllContextDataAttributes() {
        try (TestTelemetry telemetry = TestTelemetry.create()) {
            StringMap contextData = new SortedArrayStringMap();
            contextData.putValue("request.id", "request-456");
            contextData.putValue("tenant", "acme");
            contextData.putValue("otel.event.name", "order.completed");
            OpenTelemetryAppender appender = OpenTelemetryAppender.builder()
                    .setName("all-context-data-appender")
                    .setOpenTelemetry(telemetry.openTelemetry)
                    .setCaptureContextDataAttributes("*")
                    .build();

            appender.append(event(
                    new SimpleMessage("order completed"),
                    Level.INFO,
                    null,
                    null,
                    contextData,
                    null));

            LogRecordData record = telemetry.singleRecord();
            assertThat(record.getEventName()).isEqualTo("order.completed");
            assertThat(record.getAttributes().get(stringKey("request.id")))
                    .isEqualTo("request-456");
            assertThat(record.getAttributes().get(stringKey("tenant"))).isEqualTo("acme");
            assertThat(record.getAttributes().get(stringKey("otel.event.name"))).isNull();
        }
    }

    @Test
    void replaysEventsCapturedBeforeOpenTelemetryIsInstalled() {
        try (TestTelemetry telemetry = TestTelemetry.create()) {
            OpenTelemetryAppender appender = OpenTelemetryAppender.builder()
                    .setName("replay-appender")
                    .setNumLogsCapturedBeforeOtelInstall(1)
                    .build();

            appender.append(event(
                    new SimpleMessage("captured before install"),
                    Level.WARN,
                    null,
                    null,
                    new SortedArrayStringMap(),
                    null));
            appender.append(event(
                    new SimpleMessage("discarded after replay limit"),
                    Level.WARN,
                    null,
                    null,
                    new SortedArrayStringMap(),
                    null));

            appender.setOpenTelemetry(telemetry.openTelemetry);

            LogRecordData record = telemetry.singleRecord();
            assertThat(record.getBody().asString()).isEqualTo("captured before install");
        }
    }

    private static OpenTelemetryAppender newAppender(OpenTelemetry openTelemetry) {
        return OpenTelemetryAppender.builder()
                .setName("test-appender")
                .setOpenTelemetry(openTelemetry)
                .build();
    }

    private static LogEvent event(
            Message message,
            Level level,
            Marker marker,
            Throwable thrown,
            StringMap contextData,
            StackTraceElement source) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("test.logger")
                .setLevel(level)
                .setMessage(message)
                .setMarker(marker)
                .setThrown(thrown)
                .setContextData(contextData)
                .setThreadName("test-thread")
                .setThreadId(17L)
                .setSource(source)
                .setTimeMillis(1_700_000_000_000L)
                .build();
    }

    private static String codeFilePath(LogRecordData record) {
        String oldKey = record.getAttributes().get(stringKey("code.filepath"));
        return oldKey != null
                ? oldKey
                : record.getAttributes().get(stringKey("code.file.path"));
    }

    private static Long codeLineNumber(LogRecordData record) {
        Long oldKey = record.getAttributes().get(longKey("code.lineno"));
        return oldKey != null ? oldKey : record.getAttributes().get(longKey("code.line.number"));
    }

    private static final class TestTelemetry implements AutoCloseable {
        private final RecordingLogRecordExporter exporter;
        private final SdkLoggerProvider loggerProvider;
        private final OpenTelemetry openTelemetry;

        private TestTelemetry(
                RecordingLogRecordExporter exporter,
                SdkLoggerProvider loggerProvider,
                OpenTelemetry openTelemetry) {
            this.exporter = exporter;
            this.loggerProvider = loggerProvider;
            this.openTelemetry = openTelemetry;
        }

        private static TestTelemetry create() {
            RecordingLogRecordExporter exporter = new RecordingLogRecordExporter();
            SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                    .build();
            OpenTelemetry openTelemetry = new OpenTelemetry() {
                @Override
                public TracerProvider getTracerProvider() {
                    return TracerProvider.noop();
                }

                @Override
                public LoggerProvider getLogsBridge() {
                    return loggerProvider;
                }

                @Override
                public ContextPropagators getPropagators() {
                    return ContextPropagators.noop();
                }
            };
            return new TestTelemetry(exporter, loggerProvider, openTelemetry);
        }

        private LogRecordData singleRecord() {
            assertThat(exporter.records).hasSize(1);
            return exporter.records.get(0);
        }

        @Override
        public void close() {
            loggerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingLogRecordExporter implements LogRecordExporter {
        private final List<LogRecordData> records = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<LogRecordData> records) {
            this.records.addAll(records);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
