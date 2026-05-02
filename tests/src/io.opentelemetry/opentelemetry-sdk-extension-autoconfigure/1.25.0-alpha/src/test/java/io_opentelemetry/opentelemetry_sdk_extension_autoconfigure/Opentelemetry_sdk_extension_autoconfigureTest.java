/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_extension_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class Opentelemetry_sdk_extension_autoconfigureTest {
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");
    private static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT = AttributeKey.stringKey("deployment.environment");
    private static final AttributeKey<String> CUSTOM_RESOURCE = AttributeKey.stringKey("custom.resource");
    private static final TextMapSetter<Map<String, String>> MAP_SETTER = Map::put;
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new MapGetter();

    @Test
    void buildsSdkFromSuppliersCustomizersAndTypedConfigProperties() {
        AutoConfiguredOpenTelemetrySdk configured = AutoConfiguredOpenTelemetrySdk.builder()
                .registerShutdownHook(false)
                .setResultAsGlobal(false)
                .addPropertiesSupplier(Opentelemetry_sdk_extension_autoconfigureTest::comprehensiveProperties)
                .addPropertiesCustomizer(config -> {
                    Map<String, String> overrides = comprehensiveProperties();
                    overrides.put("custom.previous.service", "autoconfigured-service");
                    overrides.put("custom.list", "alpha,beta,gamma");
                    return overrides;
                })
                .addResourceCustomizer((resource, config) -> resource.merge(Resource.create(Attributes.of(
                        CUSTOM_RESOURCE, config.getString("custom.previous.service")))))
                .addSamplerCustomizer((sampler, config) -> {
                    assertThat(sampler.getDescription()).contains("TraceIdRatioBased", "0.250000");
                    return Sampler.alwaysOn();
                })
                .build();

        try {
            Resource resource = configured.getResource();
            assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo("autoconfigured-service");
            assertThat(resource.getAttribute(DEPLOYMENT_ENVIRONMENT)).isEqualTo("native test");
            assertThat(resource.getAttribute(SERVICE_VERSION)).isNull();
            assertThat(resource.getAttribute(CUSTOM_RESOURCE)).isEqualTo("autoconfigured-service");

            ConfigProperties config = configured.getConfig();
            assertThat(config.getBoolean("custom.enabled")).isTrue();
            assertThat(config.getBoolean("missing.boolean", true)).isTrue();
            assertThat(config.getInt("custom.retries")).isEqualTo(7);
            assertThat(config.getInt("missing.int", 9)).isEqualTo(9);
            assertThat(config.getLong("custom.timeout.nanos")).isEqualTo(123456789L);
            assertThat(config.getLong("missing.long", 11L)).isEqualTo(11L);
            assertThat(config.getDouble("custom.ratio")).isEqualTo(0.75d);
            assertThat(config.getDouble("missing.double", 0.5d)).isEqualTo(0.5d);
            assertThat(config.getDuration("otel.bsp.schedule.delay")).isEqualTo(Duration.ofSeconds(2));
            assertThat(config.getDuration("missing.duration", Duration.ofMillis(3)))
                    .isEqualTo(Duration.ofMillis(3));
            assertThat(config.getList("custom.list")).containsExactly("alpha", "beta", "gamma");
            assertThat(config.getList("missing.list", List.of("fallback"))).containsExactly("fallback");
            assertThat(config.getMap("custom.map")).containsEntry("first", "one").containsEntry("second", "two");
            assertThat(config.getMap("missing.map", Map.of("fallback", "value")))
                    .containsEntry("fallback", "value");
        } finally {
            configured.getOpenTelemetrySdk().close();
        }
    }

    @Test
    void defaultW3cPropagatorsInjectAndExtractTraceContextAndBaggage() {
        AutoConfiguredOpenTelemetrySdk configured =
                buildWithExportersDisabled(Map.of("otel.propagators", "tracecontext,baggage"));
        try {
            SpanContext spanContext = SpanContext.create(
                    "4bf92f3577b34da6a3ce929d0e0e4736",
                    "00f067aa0ba902b7",
                    TraceFlags.getSampled(),
                    TraceState.getDefault());
            Context context = Baggage.builder()
                    .put("tenant", "green")
                    .put("region", "emea")
                    .build()
                    .storeInContext(Span.wrap(spanContext).storeInContext(Context.root()));
            Map<String, String> carrier = new HashMap<>();

            configured.getOpenTelemetrySdk().getPropagators().getTextMapPropagator()
                    .inject(context, carrier, MAP_SETTER);

            assertThat(carrier).containsEntry(
                    "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            assertThat(carrier.get("baggage")).contains("tenant=green", "region=emea");

            Context extracted = configured.getOpenTelemetrySdk().getPropagators().getTextMapPropagator()
                    .extract(Context.root(), carrier, MAP_GETTER);
            assertThat(Span.fromContext(extracted).getSpanContext().getTraceId())
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(Baggage.fromContext(extracted).getEntryValue("tenant")).isEqualTo("green");
        } finally {
            configured.getOpenTelemetrySdk().close();
        }
    }

    @Test
    void providerCustomizersCanAddProcessorsReadersAndExportTelemetry() {
        RecordingSpanExporter spanExporter = new RecordingSpanExporter();
        RecordingLogRecordExporter logExporter = new RecordingLogRecordExporter();
        RecordingMetricReader metricReader = new RecordingMetricReader();

        Map<String, String> properties = Map.of(
                "otel.sdk.disabled", "false",
                "otel.traces.exporter", "none",
                "otel.metrics.exporter", "none",
                "otel.logs.exporter", "none",
                "otel.propagators", "tracecontext,baggage",
                "otel.traces.sampler", "parentbased_always_on",
                "otel.service.name", "customized-service");
        AutoConfiguredOpenTelemetrySdk configured = AutoConfiguredOpenTelemetrySdk.builder()
                .registerShutdownHook(false)
                .setResultAsGlobal(false)
                .addPropertiesSupplier(() -> properties)
                .addPropertiesCustomizer(config -> properties)
                .addTracerProviderCustomizer((builder, config) ->
                        builder.addSpanProcessor(SimpleSpanProcessor.create(spanExporter)))
                .addLoggerProviderCustomizer((builder, config) ->
                        builder.addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter)))
                .addMeterProviderCustomizer((builder, config) -> builder.registerMetricReader(metricReader))
                .build();

        try {
            Span span = configured.getOpenTelemetrySdk().getTracer("autoconfigure-test")
                    .spanBuilder("customized-span")
                    .setAttribute("component", "tracer-provider-customizer")
                    .startSpan();
            span.end();

            configured.getOpenTelemetrySdk().getSdkLoggerProvider().get("autoconfigure-test")
                    .logRecordBuilder()
                    .setSeverity(Severity.INFO)
                    .setBody("customized-log")
                    .setAttribute(AttributeKey.stringKey("component"), "logger-provider-customizer")
                    .emit();

            assertThat(configured.getOpenTelemetrySdk().getSdkMeterProvider().forceFlush()
                    .join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(configured.getOpenTelemetrySdk().getSdkTracerProvider().forceFlush()
                    .join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(configured.getOpenTelemetrySdk().getSdkLoggerProvider().forceFlush()
                    .join(10, TimeUnit.SECONDS).isSuccess()).isTrue();

            assertThat(spanExporter.getExportedSpans()).hasSize(1);
            SpanData exportedSpan = spanExporter.getExportedSpans().get(0);
            assertThat(exportedSpan.getName()).isEqualTo("customized-span");
            assertThat(exportedSpan.getResource().getAttribute(SERVICE_NAME)).isEqualTo("customized-service");
            assertThat(exportedSpan.getAttributes().get(AttributeKey.stringKey("component")))
                    .isEqualTo("tracer-provider-customizer");

            assertThat(logExporter.getExportedLogs()).hasSize(1);
            LogRecordData exportedLog = logExporter.getExportedLogs().get(0);
            assertThat(exportedLog.getSeverity()).isEqualTo(Severity.INFO);
            assertThat(exportedLog.getBody().asString()).isEqualTo("customized-log");
            assertThat(exportedLog.getResource().getAttribute(SERVICE_NAME)).isEqualTo("customized-service");
            assertThat(exportedLog.getAttributes().get(AttributeKey.stringKey("component")))
                    .isEqualTo("logger-provider-customizer");

            assertThat(metricReader.getRegisterCount()).isEqualTo(1);
            assertThat(metricReader.getForceFlushCount()).isEqualTo(1);
        } finally {
            configured.getOpenTelemetrySdk().close();
        }
    }

    @Test
    void disabledSdkDoesNotConfigureExportersButStillExposesResourceAndConfig() {
        Map<String, String> properties = Map.of(
                "otel.sdk.disabled", "true",
                "otel.traces.exporter", "missing-exporter-is-ignored-when-sdk-disabled",
                "otel.metrics.exporter", "missing-exporter-is-ignored-when-sdk-disabled",
                "otel.logs.exporter", "missing-exporter-is-ignored-when-sdk-disabled",
                "otel.service.name", "disabled-service");
        AutoConfiguredOpenTelemetrySdk configured = AutoConfiguredOpenTelemetrySdk.builder()
                .registerShutdownHook(false)
                .setResultAsGlobal(false)
                .addPropertiesSupplier(() -> properties)
                .addPropertiesCustomizer(config -> properties)
                .build();

        try {
            assertThat(configured.getConfig().getBoolean("otel.sdk.disabled")).isTrue();
            assertThat(configured.getResource().getAttribute(SERVICE_NAME)).isEqualTo("disabled-service");
            assertThat(configured.getOpenTelemetrySdk().getPropagators().getTextMapPropagator().fields())
                    .isEmpty();
        } finally {
            configured.getOpenTelemetrySdk().close();
        }
    }

    @Test
    void invalidConfigurationValuesFailWithClearConfigurationExceptions() {
        assertThatThrownBy(() -> buildWithExportersDisabled(Map.of("otel.propagators", "none,baggage")))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("otel.propagators contains 'none' along with other propagators");
        assertThatThrownBy(() -> buildWithExportersDisabled(Map.of("otel.propagators", "unknown")))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Unrecognized value for otel.propagators: unknown");
        assertThatThrownBy(() -> buildWithExportersDisabled(Map.of("otel.traces.sampler", "unknown")))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Unrecognized value for otel.traces.sampler: unknown");
        assertThatThrownBy(() -> buildWithExportersDisabled(Map.of("otel.traces.exporter", "none,zipkin")))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("otel.traces.exporter contains none along with other exporters");
        assertThatThrownBy(() -> buildWithExportersDisabled(Map.of("otel.metrics.exporter", "none,otlp")))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("otel.metrics.exporter contains none along with other exporters");
        assertThatThrownBy(() -> buildWithExportersDisabled(Map.of("otel.logs.exporter", "none,otlp")))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("otel.logs.exporter contains none along with other exporters");
    }

    private static Map<String, String> comprehensiveProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.sdk.disabled", "false");
        properties.put("otel.traces.exporter", "none");
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.logs.exporter", "none");
        properties.put(
                "otel.resource.attributes",
                "service.version=1.25.0-alpha,deployment.environment=native%20test");
        properties.put("otel.experimental.resource.disabled.keys", "service.version");
        properties.put("otel.service.name", "autoconfigured-service");
        properties.put("otel.propagators", "tracecontext,baggage");
        properties.put("otel.traces.sampler", "traceidratio");
        properties.put("otel.traces.sampler.arg", "0.25");
        properties.put("otel.bsp.schedule.delay", "2s");
        properties.put("custom.enabled", "true");
        properties.put("custom.retries", "7");
        properties.put("custom.timeout.nanos", "123456789");
        properties.put("custom.ratio", "0.75");
        properties.put("custom.map", "first=one,second=two");
        return properties;
    }

    private static AutoConfiguredOpenTelemetrySdk buildWithExportersDisabled(Map<String, String> overrides) {
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.sdk.disabled", "false");
        properties.put("otel.traces.exporter", "none");
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.logs.exporter", "none");
        properties.put("otel.propagators", "tracecontext,baggage");
        properties.put("otel.traces.sampler", "parentbased_always_on");
        properties.putAll(overrides);
        return AutoConfiguredOpenTelemetrySdk.builder()
                .registerShutdownHook(false)
                .setResultAsGlobal(false)
                .addPropertiesSupplier(() -> properties)
                .addPropertiesCustomizer(config -> properties)
                .build();
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<SpanData> exportedSpans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            exportedSpans.addAll(spans);
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

        private List<SpanData> getExportedSpans() {
            return exportedSpans;
        }
    }

    private static final class RecordingLogRecordExporter implements LogRecordExporter {
        private final List<LogRecordData> exportedLogs = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            exportedLogs.addAll(logs);
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

        private List<LogRecordData> getExportedLogs() {
            return exportedLogs;
        }
    }

    private static final class RecordingMetricReader implements MetricReader {
        private final AtomicInteger registerCount = new AtomicInteger();
        private final AtomicInteger forceFlushCount = new AtomicInteger();

        @Override
        public void register(CollectionRegistration registration) {
            registerCount.incrementAndGet();
        }

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.CUMULATIVE;
        }

        @Override
        public CompletableResultCode forceFlush() {
            forceFlushCount.incrementAndGet();
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        private int getRegisterCount() {
            return registerCount.get();
        }

        private int getForceFlushCount() {
            return forceFlushCount.get();
        }
    }

    private static final class MapGetter implements TextMapGetter<Map<String, String>> {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    }
}
