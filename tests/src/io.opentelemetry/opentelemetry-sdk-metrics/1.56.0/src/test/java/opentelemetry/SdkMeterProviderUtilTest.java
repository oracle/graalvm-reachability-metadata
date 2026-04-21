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
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.ViewBuilder;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.MeterConfig;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class SdkMeterProviderUtilTest {

    private static final AttributeKey<String> EXISTING_KEY = AttributeKey.stringKey("existing");
    private static final AttributeKey<String> TENANT_KEY = AttributeKey.stringKey("tenant");
    private static final AttributeKey<String> IGNORED_KEY = AttributeKey.stringKey("ignored");

    @Test
    public void setMeterConfiguratorOnBuilderAppliesConfigurationBeforeBuild() {
        InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
        SdkMeterProviderBuilder providerBuilder =
                SdkMeterProvider.builder().registerMetricReader(metricReader);

        SdkMeterProviderUtil.setMeterConfigurator(
                providerBuilder,
                scope -> "disabled-builder-scope".equals(scope.getName()) ? MeterConfig.disabled() : null);

        try (SdkMeterProvider meterProvider = providerBuilder.build()) {
            LongCounter disabledCounter = meterProvider.meterBuilder("disabled-builder-scope")
                    .build()
                    .counterBuilder("builder.counter")
                    .build();
            LongCounter enabledCounter = meterProvider.meterBuilder("enabled-builder-scope")
                    .build()
                    .counterBuilder("builder.counter")
                    .build();

            disabledCounter.add(1);
            enabledCounter.add(2);

            MetricData metric = getOnlyMetric(metricReader.collectAllMetrics());
            LongPointData point = getOnlyLongPoint(metric);

            assertThat(metric.getInstrumentationScopeInfo().getName()).isEqualTo("enabled-builder-scope");
            assertThat(metric.getName()).isEqualTo("builder.counter");
            assertThat(point.getValue()).isEqualTo(2L);
        }
    }

    @Test
    public void addMeterConfiguratorConditionDisablesOnlyMatchingMeters() {
        InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
        SdkMeterProviderBuilder providerBuilder =
                SdkMeterProvider.builder().registerMetricReader(metricReader);

        SdkMeterProviderUtil.addMeterConfiguratorCondition(
                providerBuilder,
                scope -> "condition-disabled-scope".equals(scope.getName()),
                MeterConfig.disabled());

        try (SdkMeterProvider meterProvider = providerBuilder.build()) {
            LongCounter disabledCounter = meterProvider.meterBuilder("condition-disabled-scope")
                    .build()
                    .counterBuilder("condition.counter")
                    .build();
            LongCounter enabledCounter = meterProvider.meterBuilder("condition-enabled-scope")
                    .build()
                    .counterBuilder("condition.counter")
                    .build();

            disabledCounter.add(3);
            enabledCounter.add(4);

            MetricData metric = getOnlyMetric(metricReader.collectAllMetrics());
            LongPointData point = getOnlyLongPoint(metric);

            assertThat(metric.getInstrumentationScopeInfo().getName()).isEqualTo("condition-enabled-scope");
            assertThat(metric.getName()).isEqualTo("condition.counter");
            assertThat(point.getValue()).isEqualTo(4L);
        }
    }

    @Test
    public void setMeterConfiguratorOnProviderUpdatesExistingMeters() {
        InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();

        try (SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build()) {
            LongCounter counter = meterProvider.meterBuilder("runtime-scope")
                    .build()
                    .counterBuilder("runtime.counter")
                    .build();

            counter.add(5);
            MetricData metricBeforeUpdate = getOnlyMetric(metricReader.collectAllMetrics());
            LongPointData pointBeforeUpdate = getOnlyLongPoint(metricBeforeUpdate);
            assertThat(pointBeforeUpdate.getValue()).isEqualTo(5L);

            SdkMeterProviderUtil.setMeterConfigurator(
                    meterProvider,
                    scope -> "runtime-scope".equals(scope.getName()) ? MeterConfig.disabled() : null);

            counter.add(6);
            assertThat(metricReader.collectAllMetrics()).isEmpty();
        }
    }

    @Test
    public void appendFilteredBaggageAttributesAddsMatchingBaggageEntriesToMetrics() {
        InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
        ViewBuilder viewBuilder = View.builder();
        SdkMeterProviderUtil.appendFilteredBaggageAttributes(viewBuilder, "tenant"::equals);

        try (SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .registerView(
                        InstrumentSelector.builder().setName("baggage.counter").build(),
                        viewBuilder.build())
                .build()) {
            LongCounter counter = meterProvider.meterBuilder("baggage-scope")
                    .build()
                    .counterBuilder("baggage.counter")
                    .build();

            try (Scope ignored = Baggage.builder()
                    .put("tenant", "alpha")
                    .put("ignored", "beta")
                    .build()
                    .storeInContext(Context.current())
                    .makeCurrent()) {
                counter.add(7, Attributes.of(EXISTING_KEY, "present"));
            }

            MetricData metric = getOnlyMetric(metricReader.collectAllMetrics());
            LongPointData point = getOnlyLongPoint(metric);

            assertThat(point.getValue()).isEqualTo(7L);
            assertThat(point.getAttributes().get(EXISTING_KEY)).isEqualTo("present");
            assertThat(point.getAttributes().get(TENANT_KEY)).isEqualTo("alpha");
            assertThat(point.getAttributes().asMap()).doesNotContainKey(IGNORED_KEY);
        }
    }

    @Test
    public void resetForTestClearsRegisteredInstruments() {
        InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();

        try (SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build()) {
            LongCounter firstCounter = meterProvider.meterBuilder("reset-scope")
                    .build()
                    .counterBuilder("reset.counter")
                    .build();

            firstCounter.add(8);
            MetricData metricBeforeReset = getOnlyMetric(metricReader.collectAllMetrics());
            LongPointData pointBeforeReset = getOnlyLongPoint(metricBeforeReset);
            assertThat(pointBeforeReset.getValue()).isEqualTo(8L);

            SdkMeterProviderUtil.resetForTest(meterProvider);
            assertThat(metricReader.collectAllMetrics()).isEmpty();

            LongCounter secondCounter = meterProvider.meterBuilder("reset-scope")
                    .build()
                    .counterBuilder("reset.counter")
                    .build();
            secondCounter.add(9);

            MetricData metricAfterReset = getOnlyMetric(metricReader.collectAllMetrics());
            LongPointData pointAfterReset = getOnlyLongPoint(metricAfterReset);
            assertThat(pointAfterReset.getValue()).isEqualTo(9L);
        }
    }

    private static MetricData getOnlyMetric(Collection<MetricData> metrics) {
        assertThat(metrics).hasSize(1);
        return metrics.iterator().next();
    }

    private static LongPointData getOnlyLongPoint(MetricData metric) {
        Collection<LongPointData> points = metric.getLongSumData().getPoints();
        assertThat(points).hasSize(1);
        return points.iterator().next();
    }
}
