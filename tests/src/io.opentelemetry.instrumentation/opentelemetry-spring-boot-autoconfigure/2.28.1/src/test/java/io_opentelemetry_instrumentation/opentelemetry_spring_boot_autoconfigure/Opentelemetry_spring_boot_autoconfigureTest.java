/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_spring_boot_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import java.util.HashMap;
import java.util.Map;
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

    private static ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
                .withPropertyValues(
                        "otel.traces.exporter=none",
                        "otel.metrics.exporter=none",
                        "otel.logs.exporter=none");
    }
}
