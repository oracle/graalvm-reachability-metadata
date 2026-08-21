/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class OpenTelemetrySdkLoggerTest {
    @Test
    void registeredSdkUsesConfiguredLoggerProviderForApplicationLogs() {
        GlobalOpenTelemetry.resetForTest();
        RecordingLogRecordProcessor processor = new RecordingLogRecordProcessor();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(processor)
                .build();

        try {
            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                    .setLoggerProvider(loggerProvider)
                    .buildAndRegisterGlobal();

            assertThat(sdk.getSdkLoggerProvider()).isSameAs(loggerProvider);
            assertThat(GlobalOpenTelemetry.getTracerProvider().tracerBuilder("global").build())
                    .isNotNull();

            sdk.getSdkLoggerProvider()
                    .loggerBuilder("checkout-service")
                    .setInstrumentationVersion("1.19.0")
                    .build()
                    .logRecordBuilder()
                    .setSeverity(Severity.INFO)
                    .setBody("order accepted")
                    .emit();

            assertThat(processor.records).hasSize(1);
            LogRecordData record = processor.records.get(0);
            assertThat(record.getBody().asString()).isEqualTo("order accepted");
            assertThat(record.getSeverity()).isEqualTo(Severity.INFO);
            assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo("checkout-service");
            assertThat(record.getInstrumentationScopeInfo().getVersion()).isEqualTo("1.19.0");
        } finally {
            GlobalOpenTelemetry.resetForTest();
            assertThat(loggerProvider.shutdown().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
        }
    }

    private static final class RecordingLogRecordProcessor implements LogRecordProcessor {
        private final List<LogRecordData> records = new CopyOnWriteArrayList<>();

        @Override
        public void onEmit(ReadWriteLogRecord logRecord) {
            records.add(logRecord.toLogRecordData());
        }
    }
}
