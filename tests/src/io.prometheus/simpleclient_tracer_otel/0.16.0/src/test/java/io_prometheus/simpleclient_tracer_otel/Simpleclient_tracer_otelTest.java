/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.simpleclient_tracer_otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;
import io.prometheus.client.exemplars.tracer.otel.OpenTelemetrySpanContextSupplier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class Simpleclient_tracer_otelTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String CHILD_TRACE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CHILD_SPAN_ID = "bbbbbbbbbbbbbbbb";

    @Test
    void openTelemetryIntegrationIsAvailableWhenOpenTelemetryApiIsOnTheClasspath() {
        String previousValue = System.getProperty("io.prometheus.otelExemplars");
        System.clearProperty("io.prometheus.otelExemplars");
        try {
            assertThat(OpenTelemetrySpanContextSupplier.isAvailable()).isTrue();
        } finally {
            restoreOtelExemplarsProperty(previousValue);
        }
    }

    @Test
    void openTelemetryIntegrationCanBeDisabledWithSystemProperty() {
        String previousValue = System.getProperty("io.prometheus.otelExemplars");
        System.setProperty("io.prometheus.otelExemplars", "inactive");
        try {
            assertThat(OpenTelemetrySpanContextSupplier.isAvailable()).isFalse();
        } finally {
            restoreOtelExemplarsProperty(previousValue);
        }
    }

    @Test
    void openTelemetryIntegrationDisablePropertyIsCaseInsensitive() {
        String previousValue = System.getProperty("io.prometheus.otelExemplars");
        System.setProperty("io.prometheus.otelExemplars", "InAcTiVe");
        try {
            assertThat(OpenTelemetrySpanContextSupplier.isAvailable()).isFalse();
        } finally {
            restoreOtelExemplarsProperty(previousValue);
        }
    }

    @Test
    void noCurrentSpanProducesNoTraceOrSpanIdentifierAndIsNotSampled() {
        OpenTelemetrySpanContextSupplier supplier = new OpenTelemetrySpanContextSupplier();

        assertThat(supplier.getTraceId()).isNull();
        assertThat(supplier.getSpanId()).isNull();
        assertThat(supplier.isSampled()).isFalse();
    }

    @Test
    void sampledCurrentSpanExposesTraceIdSpanIdAndSamplingDecision() {
        OpenTelemetrySpanContextSupplier supplier = new OpenTelemetrySpanContextSupplier();
        Span span = span(TRACE_ID, SPAN_ID, TraceFlags.getSampled());

        try (Scope scope = span.makeCurrent()) {
            assertThat(supplier.getTraceId()).isEqualTo(TRACE_ID);
            assertThat(supplier.getSpanId()).isEqualTo(SPAN_ID);
            assertThat(supplier.isSampled()).isTrue();
        }
    }

    @Test
    void unsampledCurrentSpanStillExposesTraceAndSpanIdentifiers() {
        OpenTelemetrySpanContextSupplier supplier = new OpenTelemetrySpanContextSupplier();
        Span span = span(TRACE_ID, SPAN_ID, TraceFlags.getDefault());

        try (Scope scope = span.makeCurrent()) {
            assertThat(supplier.getTraceId()).isEqualTo(TRACE_ID);
            assertThat(supplier.getSpanId()).isEqualTo(SPAN_ID);
            assertThat(supplier.isSampled()).isFalse();
        }
    }

    @Test
    void supplierReadsTheCurrentlyActiveSpanEveryTimeItIsQueried() {
        OpenTelemetrySpanContextSupplier supplier = new OpenTelemetrySpanContextSupplier();
        Span parentSpan = span(TRACE_ID, SPAN_ID, TraceFlags.getSampled());
        Span childSpan = span(CHILD_TRACE_ID, CHILD_SPAN_ID, TraceFlags.getDefault());

        try (Scope parentScope = parentSpan.makeCurrent()) {
            assertThat(supplier.getTraceId()).isEqualTo(TRACE_ID);
            assertThat(supplier.getSpanId()).isEqualTo(SPAN_ID);
            assertThat(supplier.isSampled()).isTrue();

            try (Scope childScope = childSpan.makeCurrent()) {
                assertThat(supplier.getTraceId()).isEqualTo(CHILD_TRACE_ID);
                assertThat(supplier.getSpanId()).isEqualTo(CHILD_SPAN_ID);
                assertThat(supplier.isSampled()).isFalse();
            }

            assertThat(supplier.getTraceId()).isEqualTo(TRACE_ID);
            assertThat(supplier.getSpanId()).isEqualTo(SPAN_ID);
            assertThat(supplier.isSampled()).isTrue();
        }

        assertThat(supplier.getTraceId()).isNull();
        assertThat(supplier.getSpanId()).isNull();
        assertThat(supplier.isSampled()).isFalse();
    }

    @Test
    void supplierReadsSpanFromWrappedOpenTelemetryContextOnWorkerThread() throws Exception {
        OpenTelemetrySpanContextSupplier supplier = new OpenTelemetrySpanContextSupplier();
        Span span = span(TRACE_ID, SPAN_ID, TraceFlags.getSampled());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            executor.submit(() -> {
            }).get(5, TimeUnit.SECONDS);

            try (Scope scope = span.makeCurrent()) {
                assertThat(executor.submit(() -> exemplarLabelsFor(supplier)).get(5, TimeUnit.SECONDS)).isEmpty();

                Callable<Map<String, String>> readPropagatedSpan = Context.current()
                        .wrap(() -> exemplarLabelsFor(supplier));

                assertThat(executor.submit(readPropagatedSpan).get(5, TimeUnit.SECONDS))
                        .containsExactly(
                                Map.entry("trace_id", TRACE_ID),
                                Map.entry("span_id", SPAN_ID));
            }

            assertThat(executor.submit(() -> exemplarLabelsFor(supplier)).get(5, TimeUnit.SECONDS)).isEmpty();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void supplierCanBeConsumedThroughTheCommonSpanContextSupplierApi() {
        SpanContextSupplier supplier = new OpenTelemetrySpanContextSupplier();
        Span span = span(TRACE_ID, SPAN_ID, TraceFlags.getSampled());

        try (Scope scope = span.makeCurrent()) {
            assertThat(exemplarLabelsFor(supplier))
                    .containsExactly(
                            Map.entry("trace_id", TRACE_ID),
                            Map.entry("span_id", SPAN_ID));
        }
    }

    @Test
    void unsampledOpenTelemetrySpanIsNotSelectedForExemplarLabels() {
        SpanContextSupplier supplier = new OpenTelemetrySpanContextSupplier();
        Span span = span(TRACE_ID, SPAN_ID, TraceFlags.getDefault());

        try (Scope scope = span.makeCurrent()) {
            assertThat(exemplarLabelsFor(supplier)).isEmpty();
        }
    }

    private static Span span(String traceId, String spanId, TraceFlags traceFlags) {
        SpanContext spanContext = SpanContext.create(traceId, spanId, traceFlags, TraceState.getDefault());
        return Span.wrap(spanContext);
    }

    private static Map<String, String> exemplarLabelsFor(SpanContextSupplier supplier) {
        if (supplier.getTraceId() == null || supplier.getSpanId() == null || !supplier.isSampled()) {
            return Map.of();
        }

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("trace_id", supplier.getTraceId());
        labels.put("span_id", supplier.getSpanId());
        return labels;
    }

    private static void restoreOtelExemplarsProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty("io.prometheus.otelExemplars");
        } else {
            System.setProperty("io.prometheus.otelExemplars", previousValue);
        }
    }
}
