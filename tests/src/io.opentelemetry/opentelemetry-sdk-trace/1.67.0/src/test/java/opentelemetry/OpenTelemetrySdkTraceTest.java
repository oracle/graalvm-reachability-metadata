/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OpenTelemetrySdkTraceTest {

    @Test
    public void batchSpanProcessorExportsQueuedSpan() {
        CapturingSpanExporter exporter = new CapturingSpanExporter();
        BatchSpanProcessor processor = BatchSpanProcessor.builder(exporter)
            .setMaxQueueSize(10)
            .setMaxExportBatchSize(10)
            .setScheduleDelay(10, TimeUnit.SECONDS)
            .build();

        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(processor)
            .build()) {
            Tracer tracer = tracerProvider.get("batch-processor-test");
            Span span = tracer.spanBuilder("queued-span").startSpan();
            span.end();

            Assertions.assertTrue(
                tracerProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess());
            List<SpanData> exportedSpans = exporter.getExportedSpans();
            Assertions.assertEquals(1, exportedSpans.size());
            Assertions.assertEquals("queued-span", exportedSpans.get(0).getName());
        }

        Assertions.assertTrue(exporter.isShutdown());
    }

    private static final class CapturingSpanExporter implements SpanExporter {
        private final AtomicReference<List<SpanData>> exportedSpans =
            new AtomicReference<>(List.of());
        private final AtomicBoolean shutdown = new AtomicBoolean();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            exportedSpans.set(List.copyOf(spans));
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown.set(true);
            return CompletableResultCode.ofSuccess();
        }

        private List<SpanData> getExportedSpans() {
            return exportedSpans.get();
        }

        private boolean isShutdown() {
            return shutdown.get();
        }
    }
}
