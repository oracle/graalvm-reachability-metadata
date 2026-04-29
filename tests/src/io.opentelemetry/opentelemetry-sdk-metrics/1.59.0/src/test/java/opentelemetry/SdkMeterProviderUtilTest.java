/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package opentelemetry;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.internal.ScopeConfigurator;
import io.opentelemetry.sdk.common.internal.ScopeConfiguratorBuilder;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.ViewBuilder;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.internal.MeterConfig;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SdkMeterProviderUtilTest {

    private static final AttributeKey<String> EXISTING_ATTRIBUTE = AttributeKey.stringKey("existing");
    private static final AttributeKey<String> TENANT_ATTRIBUTE = AttributeKey.stringKey("tenant.id");
    private static final AttributeKey<String> IGNORED_ATTRIBUTE = AttributeKey.stringKey("ignored.id");

    @Test
    public void builderConfiguratorAndConditionDisableMatchingMeters() {
        RecordingMetricReader reader = new RecordingMetricReader();
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder()
                .registerMetricReader(reader);
        ScopeConfigurator<MeterConfig> configurator = MeterConfig.configuratorBuilder()
                .addCondition(ScopeConfiguratorBuilder.nameEquals("blocked-by-configurator"), MeterConfig.disabled())
                .build();

        SdkMeterProviderUtil.setMeterConfigurator(builder, configurator);
        SdkMeterProviderUtil.addMeterConfiguratorCondition(
                builder,
                ScopeConfiguratorBuilder.nameEquals("blocked-by-condition"),
                MeterConfig.disabled());

        try (SdkMeterProvider provider = builder.build()) {
            provider.get("allowed").counterBuilder("allowed.counter").build().add(1);
            provider.get("blocked-by-configurator").counterBuilder("blocked.configurator.counter").build().add(1);
            provider.get("blocked-by-condition").counterBuilder("blocked.condition.counter").build().add(1);

            Collection<MetricData> metrics = reader.collect();

            Assertions.assertTrue(metricNamed(metrics, "allowed.counter").isPresent());
            Assertions.assertFalse(metricNamed(metrics, "blocked.configurator.counter").isPresent());
            Assertions.assertFalse(metricNamed(metrics, "blocked.condition.counter").isPresent());
        }
    }

    @Test
    public void providerConfiguratorUpdatesExistingMetersAndResetClearsInstruments() {
        RecordingMetricReader reader = new RecordingMetricReader();
        try (SdkMeterProvider provider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .build()) {
            Meter meter = provider.get("configurable");
            meter.counterBuilder("reset.counter").build().add(1);
            Assertions.assertTrue(metricNamed(reader.collect(), "reset.counter").isPresent());

            ScopeConfigurator<MeterConfig> disabledConfigurator = MeterConfig.configuratorBuilder()
                    .addCondition(ScopeConfiguratorBuilder.nameEquals("configurable"), MeterConfig.disabled())
                    .build();
            SdkMeterProviderUtil.setMeterConfigurator(provider, disabledConfigurator);
            meter.counterBuilder("disabled.after.update.counter").build().add(1);
            Assertions.assertFalse(metricNamed(reader.collect(), "disabled.after.update.counter").isPresent());

            SdkMeterProviderUtil.setMeterConfigurator(provider, MeterConfig.configuratorBuilder().build());
            meter.counterBuilder("after.reenable.counter").build().add(1);
            Assertions.assertTrue(metricNamed(reader.collect(), "after.reenable.counter").isPresent());

            SdkMeterProviderUtil.resetForTest(provider);
            Assertions.assertFalse(metricNamed(reader.collect(), "after.reenable.counter").isPresent());
        }
    }

    @Test
    public void baggageAttributesAreAppendedToViewMeasurements() {
        RecordingMetricReader reader = new RecordingMetricReader();
        ViewBuilder viewBuilder = View.builder().setAttributeFilter(key -> EXISTING_ATTRIBUTE.getKey().equals(key));
        Predicate<String> baggageKeyFilter = key -> TENANT_ATTRIBUTE.getKey().equals(key);
        SdkMeterProviderUtil.appendFilteredBaggageAttributes(viewBuilder, baggageKeyFilter);
        View view = viewBuilder.build();

        try (SdkMeterProvider provider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .registerView(InstrumentSelector.builder().setName("baggage.counter").build(), view)
                .build()) {
            Context context = Baggage.builder()
                    .put(TENANT_ATTRIBUTE.getKey(), "tenant-a")
                    .put(IGNORED_ATTRIBUTE.getKey(), "ignored")
                    .put(EXISTING_ATTRIBUTE.getKey(), "from-baggage")
                    .build()
                    .storeInContext(Context.root());

            provider.get("baggage-meter").counterBuilder("baggage.counter").build()
                    .add(1, Attributes.of(EXISTING_ATTRIBUTE, "from-measurement"), context);

            MetricData metric = metricNamed(reader.collect(), "baggage.counter").orElseThrow(AssertionError::new);
            LongPointData point = metric.getLongSumData().getPoints().iterator().next();

            Assertions.assertEquals("from-measurement", point.getAttributes().get(EXISTING_ATTRIBUTE));
            Assertions.assertEquals("tenant-a", point.getAttributes().get(TENANT_ATTRIBUTE));
            Assertions.assertNull(point.getAttributes().get(IGNORED_ATTRIBUTE));
        }
    }

    private static Optional<MetricData> metricNamed(Collection<MetricData> metrics, String name) {
        return metrics.stream()
                .filter(metric -> name.equals(metric.getName()))
                .findFirst();
    }

    private static final class RecordingMetricReader implements MetricReader {
        private CollectionRegistration registration = CollectionRegistration.noop();

        @Override
        public void register(CollectionRegistration registration) {
            this.registration = registration;
        }

        Collection<MetricData> collect() {
            return registration.collectAllMetrics();
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
            return CompletableResultCode.ofSuccess();
        }
    }
}
