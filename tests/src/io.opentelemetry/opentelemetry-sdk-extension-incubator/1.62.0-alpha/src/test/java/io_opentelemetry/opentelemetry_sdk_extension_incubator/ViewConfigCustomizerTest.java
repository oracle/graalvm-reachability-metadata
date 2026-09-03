/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_extension_incubator;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.extension.incubator.metric.viewconfig.ViewConfigCustomizer;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ViewConfigCustomizerTest {
    private static final String VIEW_CONFIG_PROPERTY = "otel.experimental.metrics.view.config";
    private static final String VIEW_CONFIG_RESOURCE =
            "classpath:/io_opentelemetry/opentelemetry_sdk_extension_incubator/view-config.yaml";

    @Test
    void loadsClasspathViewConfigurationThroughAutoConfigurationCustomizer() {
        CollectingMetricReader reader = new CollectingMetricReader();
        CapturingAutoConfigurationCustomizer autoConfiguration =
                new CapturingAutoConfigurationCustomizer();
        new ViewConfigCustomizer().customize(autoConfiguration);

        SdkMeterProviderBuilder builder = SdkMeterProvider.builder().registerMetricReader(reader);
        ConfigProperties configProperties = new TestConfigProperties(
                Map.of(VIEW_CONFIG_PROPERTY, List.of(VIEW_CONFIG_RESOURCE)));
        SdkMeterProvider meterProvider = autoConfiguration
                .customize(builder, configProperties)
                .build();
        try {
            Meter meter = meterProvider.get("view-config-test-meter");
            DoubleHistogram histogram = meter.histogramBuilder("request.duration")
                    .setDescription("original description")
                    .setUnit("ms")
                    .build();
            histogram.record(
                    150.0,
                    Attributes.of(
                            AttributeKey.stringKey("method"),
                            "GET",
                            AttributeKey.stringKey("dropped"),
                            "unused"));

            Collection<MetricData> metrics = reader.collectAllMetrics();

            assertThat(metrics).singleElement().satisfies(metric -> {
                assertThat(metric.getName()).isEqualTo("configured.request.duration");
                assertThat(metric.getDescription()).isEqualTo("configured description");
                assertThat(metric.getUnit()).isEqualTo("ms");
                assertThat(metric.getType()).isEqualTo(MetricDataType.HISTOGRAM);

                HistogramPointData point = metric.getHistogramData().getPoints().iterator().next();
                assertThat(point.getSum()).isEqualTo(150.0);
                assertThat(point.getCount()).isEqualTo(1);
                assertThat(point.getBoundaries()).containsExactly(100.0, 200.0);
                assertThat(point.getAttributes())
                        .isEqualTo(Attributes.of(AttributeKey.stringKey("method"), "GET"));
            });
        } finally {
            meterProvider.close();
        }
    }

    private static final class CapturingAutoConfigurationCustomizer
            implements AutoConfigurationCustomizer {
        private BiFunction<SdkMeterProviderBuilder, ConfigProperties, SdkMeterProviderBuilder>
                meterProviderCustomizer;

        SdkMeterProviderBuilder customize(
                SdkMeterProviderBuilder builder, ConfigProperties configProperties) {
            assertThat(meterProviderCustomizer).isNotNull();
            return meterProviderCustomizer.apply(builder, configProperties);
        }

        @Override
        public AutoConfigurationCustomizer addMeterProviderCustomizer(
                BiFunction<SdkMeterProviderBuilder, ConfigProperties, SdkMeterProviderBuilder>
                        customizer) {
            meterProviderCustomizer = customizer;
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addPropagatorCustomizer(
                BiFunction<? super TextMapPropagator, ConfigProperties, ? extends TextMapPropagator>
                        propagatorCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addResourceCustomizer(
                BiFunction<? super Resource, ConfigProperties, ? extends Resource>
                        resourceCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSamplerCustomizer(
                BiFunction<? super Sampler, ConfigProperties, ? extends Sampler>
                        samplerCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSpanExporterCustomizer(
                BiFunction<? super SpanExporter, ConfigProperties, ? extends SpanExporter>
                        spanExporterCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addSpanProcessorCustomizer(
                BiFunction<? super SpanProcessor, ConfigProperties, ? extends SpanProcessor>
                        spanProcessorCustomizer) {
            return this;
        }

        @Override
        public AutoConfigurationCustomizer addPropertiesSupplier(
                Supplier<Map<String, String>> propertiesSupplier) {
            return this;
        }
    }

    private static final class TestConfigProperties implements ConfigProperties {
        private final Map<String, List<String>> lists;

        private TestConfigProperties(Map<String, List<String>> lists) {
            this.lists = lists;
        }

        @Override
        public String getString(String name) {
            return null;
        }

        @Override
        public Boolean getBoolean(String name) {
            return null;
        }

        @Override
        public Integer getInt(String name) {
            return null;
        }

        @Override
        public Long getLong(String name) {
            return null;
        }

        @Override
        public Double getDouble(String name) {
            return null;
        }

        @Override
        public Duration getDuration(String name) {
            return null;
        }

        @Override
        public List<String> getList(String name) {
            return lists.getOrDefault(name, Collections.emptyList());
        }

        @Override
        public Map<String, String> getMap(String name) {
            return Collections.emptyMap();
        }
    }

    private static final class CollectingMetricReader implements MetricReader {
        private CollectionRegistration registration = CollectionRegistration.noop();
        private boolean closed;

        @Override
        public void register(CollectionRegistration registration) {
            this.registration = registration;
        }

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.CUMULATIVE;
        }

        @Override
        public CompletableResultCode forceFlush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            closed = true;
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public void close() {
            shutdown();
        }

        private Collection<MetricData> collectAllMetrics() {
            assertThat(closed).isFalse();
            return registration.collectAllMetrics();
        }
    }
}
