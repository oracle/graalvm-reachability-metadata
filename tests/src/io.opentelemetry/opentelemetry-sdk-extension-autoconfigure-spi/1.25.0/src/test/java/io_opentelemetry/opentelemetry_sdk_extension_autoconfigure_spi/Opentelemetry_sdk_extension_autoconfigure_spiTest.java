/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_extension_autoconfigure_spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.logs.ConfigurableLogRecordExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSamplerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.SdkTracerProviderConfigurer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class Opentelemetry_sdk_extension_autoconfigure_spiTest {
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    @Test
    void configPropertiesDefaultMethodsReturnConfiguredAndFallbackValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("string", "configured");
        values.put("boolean", Boolean.TRUE);
        values.put("int", 7);
        values.put("long", 42L);
        values.put("double", 3.5D);
        values.put("duration", Duration.ofSeconds(5));
        values.put("list", List.of("alpha", "beta"));
        values.put("map", Map.of("left", "right"));
        ConfigProperties configProperties = new MapBackedConfigProperties(values);

        assertThat(configProperties.getString("string", "fallback")).isEqualTo("configured");
        assertThat(configProperties.getBoolean("boolean", false)).isTrue();
        assertThat(configProperties.getInt("int", 1)).isEqualTo(7);
        assertThat(configProperties.getLong("long", 1L)).isEqualTo(42L);
        assertThat(configProperties.getDouble("double", 1.0D)).isEqualTo(3.5D);
        assertThat(configProperties.getDuration("duration", Duration.ofMillis(1))).isEqualTo(Duration.ofSeconds(5));
        assertThat(configProperties.getList("list", List.of("fallback"))).containsExactly("alpha", "beta");
        assertThat(configProperties.getMap("map", Map.of("fallback", "value"))).containsEntry("left", "right");

        assertThat(configProperties.getString("missing", "fallback")).isEqualTo("fallback");
        assertThat(configProperties.getBoolean("missing", true)).isTrue();
        assertThat(configProperties.getInt("missing", 11)).isEqualTo(11);
        assertThat(configProperties.getLong("missing", 12L)).isEqualTo(12L);
        assertThat(configProperties.getDouble("missing", 13.0D)).isEqualTo(13.0D);
        assertThat(configProperties.getDuration("missing", Duration.ofMinutes(2))).isEqualTo(Duration.ofMinutes(2));
        assertThat(configProperties.getList("missing", List.of("fallback"))).containsExactly("fallback");
        assertThat(configProperties.getMap("missing", Map.of("fallback", "value"))).containsEntry("fallback", "value");
    }

    @Test
    void orderedProvidersExposeDefaultAndOverriddenOrder() {
        ResourceProvider defaultOrderedProvider = config -> Resource.empty();
        ResourceProvider customOrderedProvider = new ResourceProvider() {
            @Override
            public Resource createResource(ConfigProperties config) {
                return Resource.empty();
            }

            @Override
            public int order() {
                return 99;
            }
        };
        AutoConfigurationCustomizerProvider defaultCustomizerProvider = customizer -> {
        };

        assertThat(defaultOrderedProvider.order()).isZero();
        assertThat(customOrderedProvider.order()).isEqualTo(99);
        assertThat(defaultCustomizerProvider.order()).isZero();
    }

    @Test
    void resourceAndPropagatorProvidersUseConfiguration() {
        ConfigProperties configProperties = new MapBackedConfigProperties(Map.of("service.name", "orders"));
        ResourceProvider resourceProvider = config -> Resource.create(
                Attributes.of(SERVICE_NAME, config.getString("service.name", "unknown")));
        ConfigurablePropagatorProvider propagatorProvider = new ConfigurablePropagatorProvider() {
            @Override
            public TextMapPropagator getPropagator(ConfigProperties config) {
                return TextMapPropagator.noop();
            }

            @Override
            public String getName() {
                return "noop-test";
            }
        };

        Resource resource = resourceProvider.createResource(configProperties);
        TextMapPropagator propagator = propagatorProvider.getPropagator(configProperties);
        Context context = Context.root();

        assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo("orders");
        assertThat(propagatorProvider.getName()).isEqualTo("noop-test");
        assertThat(propagator.fields()).isEmpty();
        assertThat(propagator.extract(context, new Object(), ignoredGetter())).isSameAs(context);
    }

    @Test
    void samplerAndExporterProvidersCreateUsableComponents() {
        ConfigProperties configProperties = new MapBackedConfigProperties(Map.of("sample", Boolean.TRUE));
        ConfigurableSamplerProvider samplerProvider = new ConfigurableSamplerProvider() {
            @Override
            public Sampler createSampler(ConfigProperties config) {
                return config.getBoolean("sample", false) ? Sampler.alwaysOn() : Sampler.alwaysOff();
            }

            @Override
            public String getName() {
                return "configured-sampler";
            }
        };
        RecordingSpanExporter spanExporter = new RecordingSpanExporter();
        ConfigurableSpanExporterProvider spanExporterProvider = new ConfigurableSpanExporterProvider() {
            @Override
            public SpanExporter createExporter(ConfigProperties config) {
                return spanExporter;
            }

            @Override
            public String getName() {
                return "recording-spans";
            }
        };

        Sampler sampler = samplerProvider.createSampler(configProperties);
        SpanExporter createdExporter = spanExporterProvider.createExporter(configProperties);

        assertThat(samplerProvider.getName()).isEqualTo("configured-sampler");
        assertThat(sampler.shouldSample(Context.root(), "trace-id", "span", SpanKind.INTERNAL, Attributes.empty(),
                Collections.<LinkData>emptyList()).getDecision()).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
        assertThat(spanExporterProvider.getName()).isEqualTo("recording-spans");
        assertThat(createdExporter.export(Collections.<SpanData>emptyList()).isSuccess()).isTrue();
        assertThat(spanExporter.exportedBatchSizes).containsExactly(0);
        assertThat(createdExporter.flush().isSuccess()).isTrue();
        assertThat(createdExporter.shutdown().isSuccess()).isTrue();
    }

    @Test
    void autoConfigurationCustomizerProviderRegistersAndAppliesSignalExporterCustomizers() {
        ExtendedRecordingAutoConfigurationCustomizer customizer = new ExtendedRecordingAutoConfigurationCustomizer();
        RecordingMetricExporter replacementMetricExporter = new RecordingMetricExporter();
        RecordingLogRecordExporter replacementLogRecordExporter = new RecordingLogRecordExporter();
        AutoConfigurationCustomizerProvider provider = target -> target
                .addMetricExporterCustomizer((exporter, config) -> replacementMetricExporter)
                .addLogRecordExporterCustomizer((exporter, config) -> replacementLogRecordExporter);
        ConfigProperties emptyConfig = new MapBackedConfigProperties(Collections.emptyMap());

        provider.customize(customizer);

        MetricExporter customizedMetricExporter = customizer.metricExporterCustomizers.get(0)
                .apply(new RecordingMetricExporter(), emptyConfig);
        LogRecordExporter customizedLogRecordExporter = customizer.logRecordExporterCustomizers.get(0)
                .apply(new RecordingLogRecordExporter(), emptyConfig);

        assertThat(customizer.metricExporterCustomizers).hasSize(1);
        assertThat(customizer.logRecordExporterCustomizers).hasSize(1);
        assertThat(customizedMetricExporter).isSameAs(replacementMetricExporter);
        assertThat(customizedMetricExporter.export(Collections.<MetricData>emptyList()).isSuccess()).isTrue();
        assertThat(replacementMetricExporter.exportedBatchSizes).containsExactly(0);
        assertThat(customizedLogRecordExporter).isSameAs(replacementLogRecordExporter);
        assertThat(customizedLogRecordExporter.export(Collections.<LogRecordData>emptyList()).isSuccess()).isTrue();
        assertThat(replacementLogRecordExporter.exportedBatchSizes).containsExactly(0);
    }

    @Test
    void metricAndLogExporterProvidersCreateUsableComponents() {
        RecordingMetricExporter metricExporter = new RecordingMetricExporter();
        ConfigurableMetricExporterProvider metricProvider = new ConfigurableMetricExporterProvider() {
            @Override
            public MetricExporter createExporter(ConfigProperties config) {
                return metricExporter;
            }

            @Override
            public String getName() {
                return "recording-metrics";
            }
        };
        RecordingLogRecordExporter logRecordExporter = new RecordingLogRecordExporter();
        ConfigurableLogRecordExporterProvider logProvider = new ConfigurableLogRecordExporterProvider() {
            @Override
            public LogRecordExporter createExporter(ConfigProperties config) {
                return logRecordExporter;
            }

            @Override
            public String getName() {
                return "recording-logs";
            }
        };
        ConfigProperties emptyConfig = new MapBackedConfigProperties(Collections.emptyMap());

        MetricExporter createdMetricExporter = metricProvider.createExporter(emptyConfig);
        LogRecordExporter createdLogRecordExporter = logProvider.createExporter(emptyConfig);

        assertThat(metricProvider.getName()).isEqualTo("recording-metrics");
        assertThat(createdMetricExporter.export(Collections.<MetricData>emptyList()).isSuccess()).isTrue();
        assertThat(createdMetricExporter.flush().isSuccess()).isTrue();
        assertThat(createdMetricExporter.shutdown().isSuccess()).isTrue();
        assertThat(createdMetricExporter.getAggregationTemporality(InstrumentType.COUNTER))
                .isEqualTo(AggregationTemporality.CUMULATIVE);
        assertThat(createdMetricExporter.getDefaultAggregation(InstrumentType.HISTOGRAM)).isNotNull();
        assertThat(metricExporter.exportedBatchSizes).containsExactly(0);

        assertThat(logProvider.getName()).isEqualTo("recording-logs");
        assertThat(createdLogRecordExporter.export(Collections.<LogRecordData>emptyList()).isSuccess()).isTrue();
        assertThat(createdLogRecordExporter.flush().isSuccess()).isTrue();
        assertThat(createdLogRecordExporter.shutdown().isSuccess()).isTrue();
        assertThat(logRecordExporter.exportedBatchSizes).containsExactly(0);
    }

    @Test
    void autoConfigurationCustomizerProviderRegistersAndAppliesCoreCustomizers() {
        RecordingAutoConfigurationCustomizer customizer = new RecordingAutoConfigurationCustomizer();
        AutoConfigurationCustomizerProvider provider = new AutoConfigurationCustomizerProvider() {
            @Override
            public void customize(AutoConfigurationCustomizer target) {
                target.addPropertiesSupplier(() -> Map.of("service.name", "checkout"))
                        .addResourceCustomizer((resource, config) -> resource.merge(Resource.create(
                                Attributes.of(SERVICE_NAME, config.getString("service.name", "unknown")))))
                        .addSamplerCustomizer((sampler, config) -> Sampler.alwaysOff())
                        .addSpanExporterCustomizer((exporter, config) -> new RecordingSpanExporter())
                        .addPropagatorCustomizer((propagator, config) -> TextMapPropagator.composite(propagator));
            }

            @Override
            public int order() {
                return 5;
            }
        };
        ConfigProperties configProperties = new MapBackedConfigProperties(Map.of("service.name", "checkout"));

        provider.customize(customizer);

        Resource customizedResource = customizer.resourceCustomizers.get(0).apply(Resource.empty(), configProperties);
        Sampler customizedSampler = customizer.samplerCustomizers.get(0).apply(Sampler.alwaysOn(), configProperties);
        SpanExporter customizedExporter = customizer.spanExporterCustomizers.get(0)
                .apply(new RecordingSpanExporter(), configProperties);
        TextMapPropagator customizedPropagator = customizer.propagatorCustomizers.get(0)
                .apply(TextMapPropagator.noop(), configProperties);

        assertThat(provider.order()).isEqualTo(5);
        assertThat(customizer.propertiesSuppliers).hasSize(1);
        assertThat(customizer.propertiesSuppliers.get(0).get()).containsEntry("service.name", "checkout");
        assertThat(customizedResource.getAttribute(SERVICE_NAME)).isEqualTo("checkout");
        assertThat(customizedSampler.shouldSample(
                Context.root(),
                "trace-id",
                "span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                Collections.<LinkData>emptyList()).getDecision()).isEqualTo(SamplingDecision.DROP);
        assertThat(customizedExporter.export(Collections.<SpanData>emptyList()).isSuccess()).isTrue();
        assertThat(customizedPropagator.fields()).isEmpty();
    }

    @Test
    void tracerProviderConfigurerUsesConfigurationToCustomizeBuilder() {
        ConfigProperties configProperties = new MapBackedConfigProperties(Map.of("record.spans", Boolean.TRUE));
        RecordingSpanProcessor spanProcessor = new RecordingSpanProcessor();
        SdkTracerProviderConfigurer configurer = new SdkTracerProviderConfigurer() {
            @Override
            public void configure(SdkTracerProviderBuilder builder, ConfigProperties config) {
                if (config.getBoolean("record.spans", false)) {
                    builder.addSpanProcessor(spanProcessor);
                }
            }
        };
        SdkTracerProviderBuilder builder = SdkTracerProvider.builder();

        configurer.configure(builder, configProperties);
        SdkTracerProvider tracerProvider = builder.build();
        try {
            Span span = tracerProvider.get("configured-test").spanBuilder("configured-span").startSpan();
            span.end();

            assertThat(spanProcessor.startedSpanNames).containsExactly("configured-span");
            assertThat(spanProcessor.endedSpanNames).containsExactly("configured-span");
            assertThat(tracerProvider.forceFlush().isSuccess()).isTrue();
        } finally {
            tracerProvider.shutdown();
        }
    }

    @Test
    void autoConfigurationCustomizerDefaultExtensionPointsAreChainableNoOps() {
        RecordingAutoConfigurationCustomizer customizer = new RecordingAutoConfigurationCustomizer();

        assertThat(customizer.addPropertiesCustomizer(config -> Map.of("unused", "value"))).isSameAs(customizer);
        assertThat(customizer.addTracerProviderCustomizer((builder, config) -> builder)).isSameAs(customizer);
        assertThat(customizer.addMeterProviderCustomizer((builder, config) -> builder)).isSameAs(customizer);
        assertThat(customizer.addMetricExporterCustomizer((exporter, config) -> exporter)).isSameAs(customizer);
        assertThat(customizer.addLoggerProviderCustomizer((builder, config) -> builder)).isSameAs(customizer);
        assertThat(customizer.addLogRecordExporterCustomizer((exporter, config) -> exporter)).isSameAs(customizer);
    }

    @Test
    void configurationExceptionPreservesMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("bad value");

        ConfigurationException withoutCause = new ConfigurationException("invalid configuration");
        ConfigurationException withCause = new ConfigurationException("invalid configuration", cause);

        assertThat(withoutCause).hasMessage("invalid configuration").hasNoCause();
        assertThat(withCause).hasMessage("invalid configuration").hasCause(cause);
    }

    private static TextMapGetter<Object> ignoredGetter() {
        return new TextMapGetter<>() {
            @Override
            public Iterable<String> keys(Object carrier) {
                return Collections.emptyList();
            }

            @Override
            public String get(Object carrier, String key) {
                return null;
            }
        };
    }

    private static final class MapBackedConfigProperties implements ConfigProperties {
        private final Map<String, Object> values;

        private MapBackedConfigProperties(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public String getString(String name) {
            return typedValue(name, String.class);
        }

        @Override
        public Boolean getBoolean(String name) {
            return typedValue(name, Boolean.class);
        }

        @Override
        public Integer getInt(String name) {
            return typedValue(name, Integer.class);
        }

        @Override
        public Long getLong(String name) {
            return typedValue(name, Long.class);
        }

        @Override
        public Double getDouble(String name) {
            return typedValue(name, Double.class);
        }

        @Override
        public Duration getDuration(String name) {
            return typedValue(name, Duration.class);
        }

        @Override
        public List<String> getList(String name) {
            Object value = values.get(name);
            if (value == null) {
                return Collections.emptyList();
            }
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) value;
            return list;
        }

        @Override
        public Map<String, String> getMap(String name) {
            Object value = values.get(name);
            if (value == null) {
                return Collections.emptyMap();
            }
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) value;
            return map;
        }

        private <T> T typedValue(String name, Class<T> valueClass) {
            Object value = values.get(name);
            if (value == null) {
                return null;
            }
            return valueClass.cast(value);
        }
    }

    private static class RecordingAutoConfigurationCustomizer implements AutoConfigurationCustomizer {
        private final List<BiFunction<? super TextMapPropagator, ConfigProperties, ? extends TextMapPropagator>>
                propagatorCustomizers = new ArrayList<>();
        private final List<BiFunction<? super Resource, ConfigProperties, ? extends Resource>> resourceCustomizers =
                new ArrayList<>();
        private final List<BiFunction<? super Sampler, ConfigProperties, ? extends Sampler>> samplerCustomizers =
                new ArrayList<>();
        private final List<BiFunction<? super SpanExporter, ConfigProperties, ? extends SpanExporter>>
                spanExporterCustomizers = new ArrayList<>();
        private final List<Supplier<Map<String, String>>> propertiesSuppliers = new ArrayList<>();

        @Override
        public AutoConfigurationCustomizer addPropagatorCustomizer(
                BiFunction<? super TextMapPropagator, ConfigProperties, ? extends TextMapPropagator>
                        propagatorCustomizer) {
            propagatorCustomizers.add(propagatorCustomizer);
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addResourceCustomizer(
                BiFunction<? super Resource, ConfigProperties, ? extends Resource> resourceCustomizer) {
            resourceCustomizers.add(resourceCustomizer);
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSamplerCustomizer(
                BiFunction<? super Sampler, ConfigProperties, ? extends Sampler> samplerCustomizer) {
            samplerCustomizers.add(samplerCustomizer);
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSpanExporterCustomizer(
                BiFunction<? super SpanExporter, ConfigProperties, ? extends SpanExporter> spanExporterCustomizer) {
            spanExporterCustomizers.add(spanExporterCustomizer);
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addPropertiesSupplier(Supplier<Map<String, String>> propertiesSupplier) {
            propertiesSuppliers.add(propertiesSupplier);
            return this;
        }
    }

    private static final class ExtendedRecordingAutoConfigurationCustomizer
            extends RecordingAutoConfigurationCustomizer {
        private final List<BiFunction<? super MetricExporter, ConfigProperties, ? extends MetricExporter>>
                metricExporterCustomizers = new ArrayList<>();
        private final List<BiFunction<? super LogRecordExporter, ConfigProperties, ? extends LogRecordExporter>>
                logRecordExporterCustomizers = new ArrayList<>();

        @Override
        public AutoConfigurationCustomizer addMetricExporterCustomizer(
                BiFunction<? super MetricExporter, ConfigProperties, ? extends MetricExporter>
                        metricExporterCustomizer) {
            metricExporterCustomizers.add(metricExporterCustomizer);
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addLogRecordExporterCustomizer(
                BiFunction<? super LogRecordExporter, ConfigProperties, ? extends LogRecordExporter>
                        logRecordExporterCustomizer) {
            logRecordExporterCustomizers.add(logRecordExporterCustomizer);
            return this;
        }
    }

    private static final class RecordingSpanProcessor implements SpanProcessor {
        private final List<String> startedSpanNames = new ArrayList<>();
        private final List<String> endedSpanNames = new ArrayList<>();

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
            startedSpanNames.add(span.getName());
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            endedSpanNames.add(span.getName());
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }
    }

    private static class RecordingSpanExporter implements SpanExporter {
        private final List<Integer> exportedBatchSizes = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            exportedBatchSizes.add(spans.size());
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

    private static final class RecordingMetricExporter implements MetricExporter {
        private final List<Integer> exportedBatchSizes = new ArrayList<>();

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.CUMULATIVE;
        }

        @Override
        public CompletableResultCode export(Collection<MetricData> metrics) {
            exportedBatchSizes.add(metrics.size());
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

    private static final class RecordingLogRecordExporter implements LogRecordExporter {
        private final List<Integer> exportedBatchSizes = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            exportedBatchSizes.add(logs.size());
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
