/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_spring_boot_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class Opentelemetry_spring_boot_autoconfigureTest {
    private static final TextMapSetter<Map<String, String>> MAP_SETTER =
            new TextMapSetter<>() {
                @Override
                public void set(Map<String, String> carrier, String key, String value) {
                    carrier.put(key, value);
                }
            };

    @Test
    void createsConfiguredOpenTelemetryAndPropagatesCurrentSpan() {
        contextRunner()
                .withPropertyValues(
                        "otel.traces.sampler=always_on", "otel.propagators=tracecontext")
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(OpenTelemetry.class)
                            .hasBean("otelProperties");

                    OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);
                    Span span = openTelemetry.getTracer("orders")
                            .spanBuilder("checkout")
                            .startSpan();
                    try (Scope ignored = span.makeCurrent()) {
                        Map<String, String> carrier = new HashMap<>();
                        openTelemetry.getPropagators().getTextMapPropagator()
                                .inject(Context.current(), carrier, MAP_SETTER);

                        assertThat(span.getSpanContext().isValid()).isTrue();
                        assertThat(carrier).containsKey("traceparent");
                        assertThat(carrier.get("traceparent")).startsWith("00-");
                    } finally {
                        span.end();
                    }
                });
    }

    @Test
    void appliesSamplerAndPropagatorPropertiesWithoutExporting() {
        contextRunner()
                .withPropertyValues("otel.traces.sampler=always_off", "otel.propagators=none")
                .run(context -> {
                    OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);
                    Span span = openTelemetry.getTracer("orders")
                            .spanBuilder("health-check")
                            .startSpan();
                    try (Scope ignored = span.makeCurrent()) {
                        Map<String, String> carrier = new HashMap<>();
                        openTelemetry.getPropagators().getTextMapPropagator()
                                .inject(Context.current(), carrier, MAP_SETTER);

                        assertThat(span.getSpanContext().isValid()).isTrue();
                        assertThat(span.isRecording()).isFalse();
                        assertThat(carrier).isEmpty();
                    } finally {
                        span.end();
                    }
                });
    }

    @Test
    void customizesMeterProviderAndExportsCounter() {
        RecordingMetricReader reader = new RecordingMetricReader();

        contextRunner()
                .withBean(
                        AutoConfigurationCustomizerProvider.class,
                        () -> customizer -> customizer.addMeterProviderCustomizer(
                                (builder, properties) -> builder.registerMetricReader(reader)))
                .run(context -> {
                    OpenTelemetrySdk openTelemetry =
                            (OpenTelemetrySdk) context.getBean(OpenTelemetry.class);
                    LongCounter counter = openTelemetry.getMeter("orders")
                            .counterBuilder("orders.processed")
                            .build();
                    counter.add(3);

                    assertThat(openTelemetry.getSdkMeterProvider().forceFlush()
                            .join(10, TimeUnit.SECONDS).isSuccess()).isTrue();

                    MetricData metric = reader.metrics.stream()
                            .filter(data -> data.getName().equals("orders.processed"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(metric.getLongSumData().getPoints()).hasSize(1);
                    LongPointData point = metric.getLongSumData().getPoints().iterator().next();
                    assertThat(point.getValue()).isEqualTo(3);
                });
    }

    @Test
    void disabledSdkProvidesNoopOpenTelemetry() {
        contextRunner()
                .withPropertyValues("otel.sdk.disabled=true")
                .run(context -> {
                    OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);
                    Span span = openTelemetry.getTracer("orders")
                            .spanBuilder("disabled-operation")
                            .startSpan();
                    try {
                        assertThat(span.getSpanContext().isValid()).isFalse();
                        assertThat(span.isRecording()).isFalse();
                    } finally {
                        span.end();
                    }
                });
    }

    @Test
    void preservesApplicationOpenTelemetryBeanAndAddsFallbackProperties() {
        OpenTelemetry applicationOpenTelemetry = OpenTelemetry.noop();

        contextRunner()
                .withBean(OpenTelemetry.class, () -> applicationOpenTelemetry)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context.getBean(OpenTelemetry.class))
                            .isSameAs(applicationOpenTelemetry);
                    assertThat(context).hasBean("otelProperties");
                    assertThat(context).doesNotHaveBean("autoConfiguredOpenTelemetrySdk");
                });
    }

    @Test
    void appliesSpringResourceAttributesToExportedSpan() {
        RecordingSpanExporter exporter = new RecordingSpanExporter();

        contextRunner()
                .withBean(
                        AutoConfigurationCustomizerProvider.class,
                        () -> new AutoConfigurationCustomizerProvider() {
                            @Override
                            public void customize(AutoConfigurationCustomizer customizer) {
                                customizer.addTracerProviderCustomizer(
                                        (builder, properties) -> builder.addSpanProcessor(
                                                SimpleSpanProcessor.create(exporter)));
                            }
                        })
                .withPropertyValues(
                        "otel.traces.sampler=always_on",
                        "otel.resource.attributes=deployment.environment=testing")
                .run(context -> {
                    OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);
                    Span span = openTelemetry.getTracer("orders")
                            .spanBuilder("checkout")
                            .startSpan();
                    try {
                        assertThat(span.getSpanContext().isValid()).isTrue();
                    } finally {
                        span.end();
                    }

                    assertThat(exporter.finishedSpans).hasSize(1);
                    assertThat(exporter.finishedSpans.get(0).getResource()
                            .getAttribute(AttributeKey.stringKey("deployment.environment")))
                            .isEqualTo("testing");
                });
    }

    private static ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
                .withPropertyValues(
                        "otel.traces.exporter=none",
                        "otel.metrics.exporter=none",
                        "otel.logs.exporter=none");
    }

    private static final class RecordingMetricReader implements MetricReader {
        private final List<MetricData> metrics = new ArrayList<>();
        private CollectionRegistration collectionRegistration;

        @Override
        public void register(CollectionRegistration collectionRegistration) {
            this.collectionRegistration = collectionRegistration;
        }

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.CUMULATIVE;
        }

        @Override
        public CompletableResultCode forceFlush() {
            metrics.addAll(collectionRegistration.collectAllMetrics());
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<SpanData> finishedSpans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            finishedSpans.addAll(spans);
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
