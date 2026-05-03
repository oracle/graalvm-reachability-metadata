/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_extension_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Opentelemetry_sdk_extension_autoconfigureTest {
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_NAMESPACE = AttributeKey.stringKey("service.namespace");
    private static final AttributeKey<String> SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id");
    private static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT =
            AttributeKey.stringKey("deployment.environment");
    private static final AttributeKey<String> CUSTOM_RESOURCE = AttributeKey.stringKey("test.custom.resource");
    private static final TextMapSetter<Map<String, String>> MAP_SETTER =
            (carrier, key, value) -> carrier.put(key, value);
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    @Test
    void buildsSdkFromPropertiesAndAppliesAllCoreCustomizers() {
        RecordingSpanExporter spanExporter = new RecordingSpanExporter();
        AtomicReference<String> valueSeenByPropertiesCustomizer = new AtomicReference<>();
        AtomicInteger resourceCustomizations = new AtomicInteger();
        AtomicInteger samplerCustomizations = new AtomicInteger();
        AtomicInteger propagatorCustomizations = new AtomicInteger();
        AtomicInteger tracerProviderCustomizations = new AtomicInteger();
        AtomicInteger meterProviderCustomizations = new AtomicInteger();
        AtomicInteger loggerProviderCustomizations = new AtomicInteger();
        AtomicInteger spanExporterCustomizations = new AtomicInteger();
        AtomicReference<ConfigProperties> configuredConfig = new AtomicReference<>();
        AtomicReference<Resource> configuredResource = new AtomicReference<>();
        AutoConfiguredOpenTelemetrySdk configured = null;

        try {
            configured = AutoConfiguredOpenTelemetrySdk.builder()
                    .disableShutdownHook()
                    .setServiceClassLoader(Opentelemetry_sdk_extension_autoconfigureTest.class.getClassLoader())
                    .addPropertiesSupplier(() -> Map.of("test.customizer.input", "from-supplier"))
                    .addPropertiesCustomizer(config -> {
                        valueSeenByPropertiesCustomizer.set(config.getString("test.customizer.input"));
                        return workingProperties();
                    })
                    .addResourceCustomizer((resource, config) -> {
                        resourceCustomizations.incrementAndGet();
                        configuredConfig.set(config);
                        Resource customResource = Resource.create(Attributes.of(
                                CUSTOM_RESOURCE, config.getString("test.custom.resource")));
                        Resource customizedResource = resource.merge(customResource);
                        configuredResource.set(customizedResource);
                        return customizedResource;
                    })
                    .addSamplerCustomizer((sampler, config) -> {
                        samplerCustomizations.incrementAndGet();
                        assertThat(config.getString("otel.traces.sampler")).isEqualTo("always_off");
                        return Sampler.alwaysOn();
                    })
                    .addPropagatorCustomizer((propagator, config) -> {
                        propagatorCustomizations.incrementAndGet();
                        assertThat(config.getList("otel.propagators")).containsExactly("tracecontext", "baggage");
                        return propagator;
                    })
                    .addSpanExporterCustomizer((exporter, config) -> {
                        spanExporterCustomizations.incrementAndGet();
                        assertThat(config.getString("otel.traces.exporter")).isEqualTo("none");
                        return spanExporter;
                    })
                    .addTracerProviderCustomizer((builder, config) -> {
                        tracerProviderCustomizations.incrementAndGet();
                        assertThat(config.getInt("otel.span.attribute.count.limit")).isEqualTo(16);
                        return builder;
                    })
                    .addMeterProviderCustomizer((builder, config) -> {
                        meterProviderCustomizations.incrementAndGet();
                        assertThat(config.getString("otel.metrics.exporter")).isEqualTo("none");
                        return builder;
                    })
                    .addLoggerProviderCustomizer((builder, config) -> {
                        loggerProviderCustomizations.incrementAndGet();
                        assertThat(config.getString("otel.logs.exporter")).isEqualTo("none");
                        return builder;
                    })
                    .build();

            ConfigProperties config = configuredConfig.get();
            assertThat(config).isNotNull();
            assertThat(valueSeenByPropertiesCustomizer).hasValue("from-supplier");
            assertThat(config.getString("otel.service.name")).isEqualTo("payments");
            assertThat(config.getBoolean("otel.sdk.disabled", true)).isFalse();
            assertThat(config.getDuration("test.duration")).isEqualTo(Duration.ofSeconds(2));
            assertThat(config.getList("test.list")).containsExactly("alpha", "beta", "gamma");
            assertThat(config.getMap("test.map"))
                    .containsEntry("left", "right")
                    .containsEntry("region", "eu");

            Resource resource = configuredResource.get();
            assertThat(resource).isNotNull();
            assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo("payments");
            assertThat(resource.getAttribute(SERVICE_NAMESPACE)).isEqualTo("checkout");
            assertThat(resource.getAttribute(DEPLOYMENT_ENVIRONMENT)).isEqualTo("integration-test");
            assertThat(resource.getAttribute(CUSTOM_RESOURCE)).isEqualTo("resource-customized");

            Map<String, String> carrier = new HashMap<>();
            Context contextWithBaggage = Baggage.builder()
                    .put("tenant", "acme")
                    .build()
                    .storeInContext(Context.root());
            configured.getOpenTelemetrySdk().getPropagators().getTextMapPropagator()
                    .inject(contextWithBaggage, carrier, MAP_SETTER);
            Context extractedContext = configured.getOpenTelemetrySdk().getPropagators().getTextMapPropagator()
                    .extract(Context.root(), carrier, MAP_GETTER);

            assertThat(carrier).containsEntry("baggage", "tenant=acme");
            assertThat(Baggage.fromContext(extractedContext).getEntryValue("tenant")).isEqualTo("acme");

            Span span = configured.getOpenTelemetrySdk()
                    .getTracer("autoconfigure-test")
                    .spanBuilder("configured-span")
                    .startSpan();
            try {
                assertThat(span.getSpanContext().isSampled()).isTrue();
            } finally {
                span.end();
            }
            assertThat(configured.getOpenTelemetrySdk().getSdkTracerProvider().forceFlush().join(5, TimeUnit.SECONDS)
                    .isSuccess()).isTrue();
            assertThat(spanExporter.exportedSpanNames()).containsExactly("configured-span");

            LongCounter counter = configured.getOpenTelemetrySdk()
                    .getMeter("autoconfigure-test")
                    .counterBuilder("processed.items")
                    .build();
            counter.add(1, Attributes.of(AttributeKey.stringKey("queue"), "orders"));

            assertThat(resourceCustomizations).hasValue(1);
            assertThat(samplerCustomizations).hasValue(1);
            assertThat(propagatorCustomizations.get()).isGreaterThanOrEqualTo(1);
            assertThat(tracerProviderCustomizations).hasValue(1);
            assertThat(meterProviderCustomizations).hasValue(1);
            assertThat(loggerProviderCustomizations).hasValue(1);
            assertThat(spanExporterCustomizations).hasValue(1);
        } finally {
            if (configured != null) {
                configured.getOpenTelemetrySdk().close();
            }
        }
    }

    @Test
    void resourceDisabledKeysFilterDecodedConfiguredAttributes() {
        AtomicReference<Resource> configuredResource = new AtomicReference<>();
        AutoConfiguredOpenTelemetrySdk configured = null;

        try {
            configured = AutoConfiguredOpenTelemetrySdk.builder()
                    .disableShutdownHook()
                    .addPropertiesCustomizer(config -> {
                        Map<String, String> properties = workingProperties();
                        properties.put("otel.service.name", "filtered-service");
                        properties.put("otel.resource.attributes",
                                "service.namespace=filtered-namespace,service.instance.id=node%201,"
                                        + "deployment.environment=filtered-environment");
                        properties.put("otel.experimental.resource.disabled.keys",
                                "service.name,service.namespace,deployment.environment");
                        return properties;
                    })
                    .addResourceCustomizer((resource, config) -> {
                        configuredResource.set(resource);
                        return resource;
                    })
                    .build();

            Resource resource = configuredResource.get();
            assertThat(resource).isNotNull();
            assertThat(resource.getAttribute(SERVICE_NAME)).isNull();
            assertThat(resource.getAttribute(SERVICE_NAMESPACE)).isNull();
            assertThat(resource.getAttribute(DEPLOYMENT_ENVIRONMENT)).isNull();
            assertThat(resource.getAttribute(SERVICE_INSTANCE_ID)).isEqualTo("node 1");
        } finally {
            if (configured != null) {
                configured.getOpenTelemetrySdk().close();
            }
        }
    }

    @Test
    void disabledSdkDoesNotConfigureSignalProvidersButExposesConfigAndResource() {
        AtomicBoolean tracerProviderCustomizerCalled = new AtomicBoolean();
        AtomicReference<ConfigProperties> configuredConfig = new AtomicReference<>();
        AtomicReference<Resource> configuredResource = new AtomicReference<>();
        AutoConfiguredOpenTelemetrySdk configured = null;

        try {
            configured = AutoConfiguredOpenTelemetrySdk.builder()
                    .disableShutdownHook()
                    .addPropertiesCustomizer(config -> {
                        Map<String, String> properties = workingProperties();
                        properties.put("otel.sdk.disabled", "true");
                        return properties;
                    })
                    .addResourceCustomizer((resource, config) -> {
                        configuredConfig.set(config);
                        configuredResource.set(resource);
                        return resource;
                    })
                    .addTracerProviderCustomizer((builder, config) -> {
                        tracerProviderCustomizerCalled.set(true);
                        return builder;
                    })
                    .build();

            Span span = configured.getOpenTelemetrySdk()
                    .getTracer("disabled-sdk-test")
                    .spanBuilder("not-recorded")
                    .startSpan();
            span.end();

            ConfigProperties config = configuredConfig.get();
            Resource resource = configuredResource.get();
            assertThat(config).isNotNull();
            assertThat(resource).isNotNull();
            assertThat(config.getBoolean("otel.sdk.disabled", false)).isTrue();
            assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo("payments");
            assertThat(configured.getOpenTelemetrySdk().getPropagators().getTextMapPropagator().fields()).isEmpty();
            assertThat(tracerProviderCustomizerCalled).isFalse();
        } finally {
            if (configured != null) {
                configured.getOpenTelemetrySdk().close();
            }
        }
    }

    @Test
    void parentBasedSamplerConfigurationHonorsRemoteParentSamplingDecision() {
        AutoConfiguredOpenTelemetrySdk configured = null;

        try {
            configured = AutoConfiguredOpenTelemetrySdk.builder()
                    .disableShutdownHook()
                    .addPropertiesCustomizer(config -> {
                        Map<String, String> properties = workingProperties();
                        properties.put("otel.traces.sampler", "parentbased_traceidratio");
                        properties.put("otel.traces.sampler.arg", "0");
                        return properties;
                    })
                    .build();

            Span rootSpan = configured.getOpenTelemetrySdk()
                    .getTracer("parentbased-sampler-test")
                    .spanBuilder("root-span")
                    .startSpan();
            try {
                assertThat(rootSpan.getSpanContext().isSampled()).isFalse();
            } finally {
                rootSpan.end();
            }

            Context sampledRemoteParent = remoteParentContext(TraceFlags.getSampled());
            Span childOfSampledRemoteParent = configured.getOpenTelemetrySdk()
                    .getTracer("parentbased-sampler-test")
                    .spanBuilder("child-of-sampled-remote-parent")
                    .setParent(sampledRemoteParent)
                    .startSpan();
            try {
                assertThat(childOfSampledRemoteParent.getSpanContext().isSampled()).isTrue();
            } finally {
                childOfSampledRemoteParent.end();
            }

            Context unsampledRemoteParent = remoteParentContext(TraceFlags.getDefault());
            Span childOfUnsampledRemoteParent = configured.getOpenTelemetrySdk()
                    .getTracer("parentbased-sampler-test")
                    .spanBuilder("child-of-unsampled-remote-parent")
                    .setParent(unsampledRemoteParent)
                    .startSpan();
            try {
                assertThat(childOfUnsampledRemoteParent.getSpanContext().isSampled()).isFalse();
            } finally {
                childOfUnsampledRemoteParent.end();
            }
        } finally {
            if (configured != null) {
                configured.getOpenTelemetrySdk().close();
            }
        }
    }

    @Test
    void invalidConfigurationFailsFastWithConfigurationException() {
        AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addPropertiesCustomizer(config -> {
                    Map<String, String> properties = workingProperties();
                    properties.put("otel.traces.sampler", "not-a-sampler");
                    return properties;
                });

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Unrecognized value for otel.traces.sampler");
    }

    @Test
    void exporterConfigurationRejectsNoneMixedWithOtherExporters() {
        AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addPropertiesCustomizer(config -> {
                    Map<String, String> properties = workingProperties();
                    properties.put("otel.traces.exporter", "none,custom");
                    return properties;
                });

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("otel.traces.exporter contains none along with other exporters");
    }

    @Test
    void builderMethodsAreChainableAndValidateRequiredArguments() {
        AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();

        assertThat(builder.disableShutdownHook()).isSameAs(builder);
        assertThat(builder.setResultAsGlobal()).isSameAs(builder);
        assertThat(builder.setServiceClassLoader(getClass().getClassLoader())).isSameAs(builder);
        assertThat(builder.addPropertiesSupplier(Map::of)).isSameAs(builder);
        assertThat(builder.addPropertiesCustomizer(config -> Map.of())).isSameAs(builder);
        assertThat(builder.addResourceCustomizer((resource, config) -> resource)).isSameAs(builder);
        assertThat(builder.addSamplerCustomizer((sampler, config) -> sampler)).isSameAs(builder);
        assertThat(builder.addPropagatorCustomizer((propagator, config) -> propagator)).isSameAs(builder);
        assertThat(builder.addSpanExporterCustomizer((exporter, config) -> exporter)).isSameAs(builder);
        assertThat(builder.addTracerProviderCustomizer((providerBuilder, config) -> providerBuilder)).isSameAs(builder);
        assertThat(builder.addMeterProviderCustomizer((providerBuilder, config) -> providerBuilder)).isSameAs(builder);
        assertThat(builder.addMetricExporterCustomizer((exporter, config) -> exporter)).isSameAs(builder);
        assertThat(builder.addLoggerProviderCustomizer((providerBuilder, config) -> providerBuilder)).isSameAs(builder);
        assertThat(builder.addLogRecordExporterCustomizer((exporter, config) -> exporter)).isSameAs(builder);

        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .setServiceClassLoader(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addResourceCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addSamplerCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addPropagatorCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addSpanExporterCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addTracerProviderCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addMeterProviderCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addMetricExporterCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addLoggerProviderCustomizer(null));
        assertThatNullPointerException().isThrownBy(() -> AutoConfiguredOpenTelemetrySdk.builder()
                .addLogRecordExporterCustomizer(null));
    }

    private static Context remoteParentContext(TraceFlags traceFlags) {
        SpanContext spanContext = SpanContext.createFromRemoteParent(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                traceFlags,
                TraceState.getDefault());
        return Span.wrap(spanContext).storeInContext(Context.root());
    }

    private static Map<String, String> workingProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.sdk.disabled", "false");
        properties.put("otel.traces.exporter", "none");
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.logs.exporter", "none");
        properties.put("otel.propagators", "tracecontext,baggage");
        properties.put("otel.traces.sampler", "always_off");
        properties.put("otel.span.attribute.count.limit", "16");
        properties.put("otel.bsp.schedule.delay", "1d");
        properties.put("otel.bsp.export.timeout", "5s");
        properties.put("otel.service.name", "payments");
        properties.put("otel.resource.attributes",
                "service.namespace=checkout,deployment.environment=integration-test");
        properties.put("test.custom.resource", "resource-customized");
        properties.put("test.duration", "2s");
        properties.put("test.list", "alpha,beta,,gamma");
        properties.put("test.map", "left=right,region=eu");
        return properties;
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<String> exportedSpanNames = new ArrayList<>();
        private boolean shutdown;

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            for (SpanData span : spans) {
                exportedSpanNames.add(span.getName());
            }
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdown = true;
            return CompletableResultCode.ofSuccess();
        }

        private List<String> exportedSpanNames() {
            assertThat(shutdown).isFalse();
            return exportedSpanNames;
        }
    }
}
