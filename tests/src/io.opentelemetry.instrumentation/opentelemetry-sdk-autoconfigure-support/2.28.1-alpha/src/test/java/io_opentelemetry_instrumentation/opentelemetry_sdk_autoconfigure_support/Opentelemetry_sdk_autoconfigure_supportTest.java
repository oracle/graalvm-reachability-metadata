/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_sdk_autoconfigure_support;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.logging.internal.AbstractSpanLoggingCustomizerProvider;
import io.opentelemetry.instrumentation.resources.internal.ResourceProviderPropertiesCustomizer;
import io.opentelemetry.instrumentation.thread.internal.AddThreadDetailsSpanProcessor;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.declarativeconfig.internal.model.BatchSpanProcessorModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.ConsoleExporterModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.OtlpHttpExporterModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.SimpleSpanProcessorModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.SpanExporterModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.TracerProviderModel;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class Opentelemetry_sdk_autoconfigure_supportTest {
    private static final String DISABLED_KEY = "otel.java.disabled.resource.providers";
    private static final String ENABLED_KEY = "otel.java.enabled.resource.providers";
    private static final String AWS_EC2_PROVIDER = "io.opentelemetry.contrib.aws.resource.Ec2ResourceProvider";
    private static final String AWS_ECS_PROVIDER = "io.opentelemetry.contrib.aws.resource.EcsResourceProvider";
    private static final String AWS_LAMBDA_PROVIDER = "io.opentelemetry.contrib.aws.resource.LambdaResourceProvider";
    private static final String AZURE_VM_PROVIDER = "io.opentelemetry.contrib.azure.resource.AzureVmResourceProvider";
    private static final String GCP_PROVIDER = "io.opentelemetry.contrib.gcp.resource.GCPResourceProvider";

    @Test
    void resourceProviderCustomizerIsRegisteredAsAutoConfigurationCustomizerProvider() {
        List<AutoConfigurationCustomizerProvider> providers = new ArrayList<>();
        ServiceLoader.load(AutoConfigurationCustomizerProvider.class).forEach(providers::add);

        assertThat(providers).anySatisfy(provider -> {
            assertThat(provider).isInstanceOf(ResourceProviderPropertiesCustomizer.class);
            assertThat(provider.order()).isEqualTo(Integer.MAX_VALUE);
        });
    }

    @Test
    void resourceProviderCustomizeMethodRegistersAPropertiesCustomizer() {
        ResourceProviderPropertiesCustomizer provider = new ResourceProviderPropertiesCustomizer();
        CapturingAutoConfigurationCustomizer autoConfigurationCustomizer =
                new CapturingAutoConfigurationCustomizer();

        provider.customize(autoConfigurationCustomizer);

        assertThat(autoConfigurationCustomizer.propertiesCustomizers).hasSize(1);
        Map<String, String> customizedProperties = autoConfigurationCustomizer.propertiesCustomizers.get(0)
                .apply(new MapConfigProperties(Collections.emptyMap()));
        assertThat(splitProperty(customizedProperties.get(DISABLED_KEY)))
                .contains(AWS_EC2_PROVIDER, AZURE_VM_PROVIDER, GCP_PROVIDER);
    }

    @Test
    void resourceProviderPropertiesDisableCloudProvidersByDefaultAndPreserveUserDisabledProviders() {
        ResourceProviderPropertiesCustomizer customizer = new ResourceProviderPropertiesCustomizer();
        Map<String, String> properties = new HashMap<>();
        properties.put(DISABLED_KEY, "com.example.CustomResourceProvider");

        Map<String, String> customizedProperties = customizer.customize(new MapConfigProperties(properties));

        assertThat(customizedProperties).containsOnlyKeys(DISABLED_KEY);
        assertThat(splitProperty(customizedProperties.get(DISABLED_KEY)))
                .contains(AWS_EC2_PROVIDER, AWS_ECS_PROVIDER, AWS_LAMBDA_PROVIDER)
                .contains(AZURE_VM_PROVIDER, GCP_PROVIDER, "com.example.CustomResourceProvider");
    }

    @Test
    void resourceProviderPropertiesHonorEnabledProviderAllowList() {
        ResourceProviderPropertiesCustomizer customizer = new ResourceProviderPropertiesCustomizer();
        Map<String, String> properties = new HashMap<>();
        properties.put(ENABLED_KEY, AWS_EC2_PROVIDER + ",com.example.EnabledResourceProvider");

        Map<String, String> customizedProperties = customizer.customize(new MapConfigProperties(properties));

        assertThat(customizedProperties).containsOnlyKeys(ENABLED_KEY);
        assertThat(splitProperty(customizedProperties.get(ENABLED_KEY)))
                .contains(AWS_EC2_PROVIDER, "com.example.EnabledResourceProvider")
                .doesNotContain(AWS_ECS_PROVIDER, AZURE_VM_PROVIDER, GCP_PROVIDER);
    }

    @Test
    void resourceProviderPropertiesHonorGroupLevelEnablementFlags() {
        ResourceProviderPropertiesCustomizer customizer = new ResourceProviderPropertiesCustomizer();
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.resource.providers.aws.enabled", "true");
        properties.put("otel.resource.providers.azure.enabled", "false");

        Map<String, String> customizedProperties = customizer.customize(new MapConfigProperties(properties));

        assertThat(customizedProperties).containsOnlyKeys(DISABLED_KEY);
        assertThat(splitProperty(customizedProperties.get(DISABLED_KEY)))
                .doesNotContain(AWS_EC2_PROVIDER, AWS_ECS_PROVIDER, AWS_LAMBDA_PROVIDER)
                .contains(AZURE_VM_PROVIDER, GCP_PROVIDER);
    }

    @Test
    void addThreadDetailsSpanProcessorAddsCurrentThreadAttributesToStartedSpans() {
        AddThreadDetailsSpanProcessor threadDetailsSpanProcessor = new AddThreadDetailsSpanProcessor();
        CapturingSpanExporter spanExporter = new CapturingSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(threadDetailsSpanProcessor)
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        Thread currentThread = Thread.currentThread();

        try {
            Span span = tracerProvider.get("thread-details-test")
                    .spanBuilder("span-with-thread-details")
                    .startSpan();
            span.end();
        } finally {
            tracerProvider.shutdown().join(5, TimeUnit.SECONDS);
        }

        assertThat(threadDetailsSpanProcessor.isStartRequired()).isTrue();
        assertThat(threadDetailsSpanProcessor.isEndRequired()).isFalse();
        assertThat(threadDetailsSpanProcessor.forceFlush().isSuccess()).isTrue();
        assertThat(threadDetailsSpanProcessor.shutdown().isSuccess()).isTrue();
        assertThat(spanExporter.spans).hasSize(1);
        Attributes attributes = spanExporter.spans.get(0).getAttributes();
        assertThat(attributes.get(AddThreadDetailsSpanProcessor.THREAD_ID)).isEqualTo(currentThread.threadId());
        assertThat(attributes.get(AddThreadDetailsSpanProcessor.THREAD_NAME)).isEqualTo(currentThread.getName());
    }

    @Test
    void spanLoggingCustomizerAddsConsoleExporterWhenEnabled() {
        CapturingDeclarativeConfigurationCustomizer customizer = new CapturingDeclarativeConfigurationCustomizer();
        EnabledSpanLoggingCustomizerProvider provider = new EnabledSpanLoggingCustomizerProvider();
        OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();

        provider.customize(customizer);
        OpenTelemetryConfigurationModel customizedModel = customizer.modelCustomizers.get(0).apply(model);

        assertThat(customizedModel).isSameAs(model);
        assertThat(customizedModel.getTracerProvider()).isNotNull();
        assertThat(customizedModel.getTracerProvider().getProcessors()).hasSize(1);
        assertThat(consoleExporter(customizedModel.getTracerProvider().getProcessors().get(0))).isNotNull();
    }

    @Test
    void spanLoggingCustomizerDoesNotAddConsoleExporterWhenDisabled() {
        CapturingDeclarativeConfigurationCustomizer customizer = new CapturingDeclarativeConfigurationCustomizer();
        DisabledSpanLoggingCustomizerProvider provider = new DisabledSpanLoggingCustomizerProvider();
        OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel();

        provider.customize(customizer);
        OpenTelemetryConfigurationModel customizedModel = customizer.modelCustomizers.get(0).apply(model);

        assertThat(customizedModel).isSameAs(model);
        assertThat(customizedModel.getTracerProvider()).isNull();
    }

    @Test
    void spanLoggingCustomizerAddsProcessorListToExistingTracerProviderWhenEnabled() {
        TracerProviderModel tracerProvider = new TracerProviderModel();
        OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel()
                .withTracerProvider(tracerProvider);
        CapturingDeclarativeConfigurationCustomizer customizer = new CapturingDeclarativeConfigurationCustomizer();
        EnabledSpanLoggingCustomizerProvider provider = new EnabledSpanLoggingCustomizerProvider();

        provider.customize(customizer);
        OpenTelemetryConfigurationModel customizedModel = customizer.modelCustomizers.get(0).apply(model);

        assertThat(customizedModel).isSameAs(model);
        assertThat(customizedModel.getTracerProvider()).isSameAs(tracerProvider);
        assertThat(customizedModel.getTracerProvider().getProcessors()).hasSize(1);
        assertThat(consoleExporter(customizedModel.getTracerProvider().getProcessors().get(0))).isNotNull();
    }

    @Test
    void spanLoggingCustomizerKeepsExistingConsoleExporterSingle() {
        SpanProcessorModel existingConsoleProcessor = new SpanProcessorModel()
                .withSimple(new SimpleSpanProcessorModel()
                        .withExporter(new SpanExporterModel().withConsole(new ConsoleExporterModel())));
        OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel()
                .withTracerProvider(new TracerProviderModel()
                        .withProcessors(new ArrayList<>(List.of(existingConsoleProcessor))));
        CapturingDeclarativeConfigurationCustomizer customizer = new CapturingDeclarativeConfigurationCustomizer();
        EnabledSpanLoggingCustomizerProvider provider = new EnabledSpanLoggingCustomizerProvider();

        provider.customize(customizer);
        OpenTelemetryConfigurationModel customizedModel = customizer.modelCustomizers.get(0).apply(model);

        assertThat(customizedModel.getTracerProvider().getProcessors()).hasSize(1);
        assertThat(customizedModel.getTracerProvider().getProcessors().get(0)).isSameAs(existingConsoleProcessor);
    }

    @Test
    void spanLoggingCustomizerPreservesExistingNonConsoleProcessorWhenAddingConsoleExporter() {
        SpanProcessorModel existingBatchProcessor = new SpanProcessorModel()
                .withBatch(new BatchSpanProcessorModel()
                        .withExporter(new SpanExporterModel().withOtlpHttp(new OtlpHttpExporterModel())));
        OpenTelemetryConfigurationModel model = new OpenTelemetryConfigurationModel()
                .withTracerProvider(new TracerProviderModel()
                        .withProcessors(new ArrayList<>(List.of(existingBatchProcessor))));
        CapturingDeclarativeConfigurationCustomizer customizer = new CapturingDeclarativeConfigurationCustomizer();
        EnabledSpanLoggingCustomizerProvider provider = new EnabledSpanLoggingCustomizerProvider();

        provider.customize(customizer);
        OpenTelemetryConfigurationModel customizedModel = customizer.modelCustomizers.get(0).apply(model);

        List<SpanProcessorModel> processors = customizedModel.getTracerProvider().getProcessors();
        assertThat(processors).hasSize(2);
        assertThat(processors.get(0)).isSameAs(existingBatchProcessor);
        assertThat(processors.get(0).getBatch()).isNotNull();
        assertThat(consoleExporter(processors.get(1))).isNotNull();
    }

    private static ConsoleExporterModel consoleExporter(SpanProcessorModel processor) {
        return processor.getSimple().getExporter().getConsole();
    }

    private static List<String> splitProperty(String propertyValue) {
        if (propertyValue == null || propertyValue.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(propertyValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static final class MapConfigProperties implements ConfigProperties {
        private final Map<String, String> properties;

        private MapConfigProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public String getString(String name) {
            return properties.get(name);
        }

        @Override
        public Boolean getBoolean(String name) {
            String value = properties.get(name);
            if (value == null) {
                return null;
            }
            return Boolean.parseBoolean(value);
        }

        @Override
        public Integer getInt(String name) {
            String value = properties.get(name);
            if (value == null) {
                return null;
            }
            return Integer.parseInt(value);
        }

        @Override
        public Long getLong(String name) {
            String value = properties.get(name);
            if (value == null) {
                return null;
            }
            return Long.parseLong(value);
        }

        @Override
        public Double getDouble(String name) {
            String value = properties.get(name);
            if (value == null) {
                return null;
            }
            return Double.parseDouble(value);
        }

        @Override
        public Duration getDuration(String name) {
            String value = properties.get(name);
            if (value == null) {
                return null;
            }
            return Duration.parse(value);
        }

        @Override
        public List<String> getList(String name) {
            return splitProperty(properties.get(name));
        }

        @Override
        public Map<String, String> getMap(String name) {
            return Collections.emptyMap();
        }
    }

    private static final class CapturingSpanExporter implements SpanExporter {
        private final List<SpanData> spans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
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

    private abstract static class TestSpanLoggingCustomizerProvider
            extends AbstractSpanLoggingCustomizerProvider {
    }

    private static final class EnabledSpanLoggingCustomizerProvider extends TestSpanLoggingCustomizerProvider {
        @Override
        protected boolean isEnabled(OpenTelemetryConfigurationModel model) {
            return true;
        }
    }

    private static final class DisabledSpanLoggingCustomizerProvider extends TestSpanLoggingCustomizerProvider {
        @Override
        protected boolean isEnabled(OpenTelemetryConfigurationModel model) {
            return false;
        }
    }

    private static final class CapturingDeclarativeConfigurationCustomizer
            implements DeclarativeConfigurationCustomizer {
        private final List<Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>>
                modelCustomizers = new ArrayList<>();

        @Override
        public void addModelCustomizer(
                Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel> modelCustomizer) {
            modelCustomizers.add(modelCustomizer);
        }

        @Override
        public <T extends SpanExporter> void addSpanExporterCustomizer(
                Class<T> exporterType,
                BiFunction<T, DeclarativeConfigProperties, T> exporterCustomizer) {
            throw new AssertionError("Span exporter customizers should not be registered by this provider");
        }

        @Override
        public <T extends MetricExporter> void addMetricExporterCustomizer(
                Class<T> exporterType,
                BiFunction<T, DeclarativeConfigProperties, T> exporterCustomizer) {
            throw new AssertionError("Metric exporter customizers should not be registered by this provider");
        }

        @Override
        public <T extends LogRecordExporter> void addLogRecordExporterCustomizer(
                Class<T> exporterType,
                BiFunction<T, DeclarativeConfigProperties, T> exporterCustomizer) {
            throw new AssertionError("Log record exporter customizers should not be registered by this provider");
        }
    }

    private static final class CapturingAutoConfigurationCustomizer implements AutoConfigurationCustomizer {
        private final List<Function<ConfigProperties, Map<String, String>>> propertiesCustomizers =
                new ArrayList<>();

        @Override
        public AutoConfigurationCustomizer addPropagatorCustomizer(
                BiFunction<? super TextMapPropagator, ConfigProperties, ? extends TextMapPropagator>
                        propagatorCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addResourceCustomizer(
                BiFunction<? super Resource, ConfigProperties, ? extends Resource> resourceCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSamplerCustomizer(
                BiFunction<? super Sampler, ConfigProperties, ? extends Sampler> samplerCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSpanExporterCustomizer(
                BiFunction<? super SpanExporter, ConfigProperties, ? extends SpanExporter> exporterCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addPropertiesSupplier(
                Supplier<Map<String, String>> propertiesSupplier) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addPropertiesCustomizer(
                Function<ConfigProperties, Map<String, String>> propertiesCustomizer) {
            propertiesCustomizers.add(propertiesCustomizer);
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addTracerProviderCustomizer(
                BiFunction<SdkTracerProviderBuilder, ConfigProperties, SdkTracerProviderBuilder>
                        tracerProviderCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addMeterProviderCustomizer(
                BiFunction<SdkMeterProviderBuilder, ConfigProperties, SdkMeterProviderBuilder>
                        meterProviderCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addLoggerProviderCustomizer(
                BiFunction<SdkLoggerProviderBuilder, ConfigProperties, SdkLoggerProviderBuilder>
                        loggerProviderCustomizer) {
            return this;
        }
    }
}
