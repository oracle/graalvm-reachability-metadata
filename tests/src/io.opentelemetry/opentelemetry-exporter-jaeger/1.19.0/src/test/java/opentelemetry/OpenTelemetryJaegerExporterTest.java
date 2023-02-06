/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package opentelemetry;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.List;


public class OpenTelemetryJaegerExporterTest {

    @Test
    public void jaegerSpanExporterTest() {
        TestSpanData span = TestSpanData.builder()
                .setKind(SpanKind.INTERNAL)
                .setName("test")
                .setStatus(StatusData.ok())
                .setSpanContext(SpanContext.create(TraceId.fromLongs(321, 123), SpanId.fromLong(12345), TraceFlags.getDefault(), TraceState.getDefault()))
                .setStartEpochNanos(System.nanoTime())
                .setEndEpochNanos(System.nanoTime())
                .setHasEnded(true)
                .build();

        Assertions.assertTrue(isClassPresent("io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter"));
        try (JaegerGrpcSpanExporter exporter = JaegerGrpcSpanExporter.builder().build()) {
            exporter.export(List.of(span));
        }
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

}
