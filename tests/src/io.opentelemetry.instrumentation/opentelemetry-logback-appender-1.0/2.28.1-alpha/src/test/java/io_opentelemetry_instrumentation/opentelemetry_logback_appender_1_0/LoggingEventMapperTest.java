/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_logback_appender_1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.logstash.logback.marker.Markers;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;

public class LoggingEventMapperTest {
    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");
    private static final AttributeKey<String> ORDER_STATUS = AttributeKey.stringKey("order.status");

    @Test
    void capturesLogstashMarkerAttributesFromLoggingEvent() {
        RecordingLogRecordExporter exporter = new RecordingLogRecordExporter();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build();
        LoggerContext loggerContext = new LoggerContext();
        loggerContext.start();
        LoggingEventMapper mapper = LoggingEventMapper.builder()
                .setCaptureLogstashMarkerAttributes(true)
                .build();

        try {
            Marker marker = Markers.append("order.id", "A123");
            Marker statusMarker = Markers.append("order.status", "completed");
            LoggingEvent event = new LoggingEvent();
            event.setLoggerContext(loggerContext);
            event.setLoggerName("test.logstash.marker");
            event.setLevel(Level.INFO);
            event.setMessage("order completed");
            event.setThreadName(Thread.currentThread().getName());
            event.setTimeStamp(System.currentTimeMillis());
            event.setMDCPropertyMap(Collections.emptyMap());
            event.addMarker(marker);
            event.addMarker(statusMarker);

            mapper.emit(loggerProvider, event, -1);

            assertThat(exporter.getRecords()).hasSize(1);
            LogRecordData record = exporter.getRecords().get(0);
            assertThat(record.getBody().asString()).isEqualTo("order completed");
            assertThat(record.getAttributes().get(ORDER_ID)).isEqualTo("A123");
            assertThat(record.getAttributes().get(ORDER_STATUS)).isEqualTo("completed");
        } finally {
            loggerContext.stop();
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

        private List<LogRecordData> getRecords() {
            return Collections.unmodifiableList(records);
        }
    }
}
