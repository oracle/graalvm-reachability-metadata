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
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.internal.ScopeConfigurator;
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
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SdkMeterProviderUtilTest {

    @Test
    public void setMeterConfiguratorOnBuilderDisablesMatchingMeter() {
        CapturingMetricReader reader = new CapturingMetricReader();
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder().registerMetricReader(reader);

        SdkMeterProviderUtil.setMeterConfigurator(
                builder, disableMeterNamed("builder-disabled-meter"));

        try (SdkMeterProvider provider = builder.build()) {
            LongCounter enabledCounter =
                    createCounter(provider, "builder-enabled-meter", "builder.enabled.counter");
            LongCounter disabledCounter =
                    createCounter(provider, "builder-disabled-meter", "builder.disabled.counter");

            enabledCounter.add(2);
            disabledCounter.add(4);

            Collection<MetricData> metrics = reader.collectAllMetrics();
            assertThat(metrics).extracting(MetricData::getName)
                    .contains("builder.enabled.counter")
                    .doesNotContain("builder.disabled.counter");
            assertThat(sumValue(metrics, "builder.enabled.counter")).isEqualTo(2);
        }
    }

    @Test
    public void addMeterConfiguratorConditionDisablesMatchingMeter() {
        CapturingMetricReader reader = new CapturingMetricReader();
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder().registerMetricReader(reader);

        SdkMeterProviderUtil.addMeterConfiguratorCondition(
                builder,
                scopeInfo -> scopeInfo.getName().startsWith("conditional-disabled"),
                MeterConfig.disabled());

        try (SdkMeterProvider provider = builder.build()) {
            LongCounter enabledCounter = createCounter(
                    provider, "conditional-enabled-meter", "conditional.enabled.counter");
            LongCounter disabledCounter = createCounter(
                    provider, "conditional-disabled-meter", "conditional.disabled.counter");

            enabledCounter.add(3);
            disabledCounter.add(6);

            Collection<MetricData> metrics = reader.collectAllMetrics();
            assertThat(metrics).extracting(MetricData::getName)
                    .contains("conditional.enabled.counter")
                    .doesNotContain("conditional.disabled.counter");
            assertThat(sumValue(metrics, "conditional.enabled.counter")).isEqualTo(3);
        }
    }

    @Test
    public void setMeterConfiguratorOnProviderAndResetForTestDisableExistingMeters() {
        CapturingMetricReader reader = new CapturingMetricReader();

        try (SdkMeterProvider provider =
                SdkMeterProvider.builder().registerMetricReader(reader).build()) {
            LongCounter counter = createCounter(provider, "runtime-meter", "runtime.counter");
            counter.add(5);

            assertThat(sumValue(reader.collectAllMetrics(), "runtime.counter")).isEqualTo(5);

            SdkMeterProviderUtil.setMeterConfigurator(provider, disableMeterNamed("runtime-meter"));
            counter.add(8);

            assertThat(reader.collectAllMetrics()).extracting(MetricData::getName)
                    .doesNotContain("runtime.counter");

            SdkMeterProviderUtil.resetForTest(provider);

            assertThat(reader.collectAllMetrics()).isEmpty();
        }
    }

    @Test
    public void appendFilteredBaggageAttributesAddsMatchingBaggageAfterAttributeFiltering() {
        CapturingMetricReader reader = new CapturingMetricReader();
        ViewBuilder viewBuilder = View.builder().setAttributeFilter(Set.of("existing"));
        SdkMeterProviderUtil.appendFilteredBaggageAttributes(
                viewBuilder, key -> key.equals("tenant"));

        try (SdkMeterProvider provider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .registerView(
                        InstrumentSelector.builder()
                                .setName("baggage.counter")
                                .setType(InstrumentType.COUNTER)
                                .build(),
                        viewBuilder.build())
                .build()) {
            LongCounter counter = createCounter(provider, "baggage-meter", "baggage.counter");
            Context context = Baggage.builder()
                    .put("tenant", "alpha")
                    .put("ignored", "beta")
                    .build()
                    .storeInContext(Context.root());

            counter.add(7, Attributes.of(AttributeKey.stringKey("existing"), "kept"), context);

            LongPointData point = getOnlyPoint(reader.collectAllMetrics(), "baggage.counter");
            assertThat(point.getValue()).isEqualTo(7);
            assertThat(point.getAttributes().get(AttributeKey.stringKey("existing")))
                    .isEqualTo("kept");
            assertThat(point.getAttributes().get(AttributeKey.stringKey("tenant")))
                    .isEqualTo("alpha");
            assertThat(point.getAttributes().get(AttributeKey.stringKey("ignored"))).isNull();
        }
    }

    private static ScopeConfigurator<MeterConfig> disableMeterNamed(String meterName) {
        return scopeInfo -> scopeInfo.getName().equals(meterName)
                ? MeterConfig.disabled()
                : MeterConfig.enabled();
    }

    private static LongCounter createCounter(
            SdkMeterProvider provider, String meterName, String counterName) {
        return provider.meterBuilder(meterName).build().counterBuilder(counterName).build();
    }

    private static long sumValue(Collection<MetricData> metrics, String metricName) {
        MetricData metric = metrics.stream()
                .filter(candidate -> candidate.getName().equals(metricName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric: " + metricName));
        return metric.getLongSumData().getPoints().stream()
                .mapToLong(LongPointData::getValue)
                .sum();
    }

    private static LongPointData getOnlyPoint(Collection<MetricData> metrics, String metricName) {
        MetricData metric = metrics.stream()
                .filter(candidate -> candidate.getName().equals(metricName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric: " + metricName));
        assertThat(metric.getLongSumData().getPoints()).hasSize(1);
        return metric.getLongSumData().getPoints().iterator().next();
    }

    private static final class CapturingMetricReader implements MetricReader {

        private CollectionRegistration registration = CollectionRegistration.noop();

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
            return CompletableResultCode.ofSuccess();
        }

        Collection<MetricData> collectAllMetrics() {
            return registration.collectAllMetrics();
        }
    }
}
