/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.micrometer.metrics.MaximumAllowableTagsMeterFilter;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.AvailableTag;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.MetricDescriptor;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterValue;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.PropertiesMeterFilter;
import org.springframework.boot.micrometer.metrics.autoconfigure.ServiceLevelObjectiveBoundary;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.datadog.DatadogProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.properties.PushRegistryPropertiesConfigAdapter;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimplePropertiesConfigAdapter;
import org.springframework.boot.micrometer.metrics.startup.StartupTimeMetricsListener;
import org.springframework.boot.micrometer.metrics.system.DiskSpaceMetricsBinder;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class SpringBootMicrometerMetricsTest {

    @Test
    void metricsEndpointAggregatesMeasurementsAndFiltersByTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            Counter.builder("orders.processed")
                    .description("Processed orders")
                    .baseUnit("orders")
                    .tag("region", "us-east")
                    .register(registry)
                    .increment(3.0);
            Counter.builder("orders.processed")
                    .description("Processed orders")
                    .baseUnit("orders")
                    .tag("region", "eu-west")
                    .register(registry)
                    .increment(2.0);

            MetricsEndpoint endpoint = new MetricsEndpoint(registry);

            assertThat(endpoint.listNames().getNames()).contains("orders.processed");
            MetricDescriptor allRegions = endpoint.metric("orders.processed", null);
            assertThat(allRegions.getName()).isEqualTo("orders.processed");
            assertThat(allRegions.getDescription()).isEqualTo("Processed orders");
            assertThat(allRegions.getBaseUnit()).isEqualTo("orders");
            assertThat(measurementValue(allRegions, Statistic.COUNT)).isCloseTo(5.0, within(0.001));
            assertThat(valuesForTag(allRegions, "region")).containsExactlyInAnyOrder("us-east", "eu-west");

            MetricDescriptor usEast = endpoint.metric("orders.processed", List.of("region:us-east"));
            assertThat(measurementValue(usEast, Statistic.COUNT)).isCloseTo(3.0, within(0.001));
            assertThat(usEast.getAvailableTags()).extracting(AvailableTag::getTag).doesNotContain("region");
        } finally {
            registry.close();
        }
    }

    @Test
    void propertiesMeterFilterAppliesCommonTagsEnableRulesAndDistributionSettings() {
        MetricsProperties properties = new MetricsProperties();
        properties.getTags().put("application", "store");
        properties.getTags().put("cluster", "blue");
        properties.getEnable().put("http.server", false);
        properties.getEnable().put("http.server.requests", true);
        properties.getDistribution().getPercentilesHistogram().put("http.server", true);
        properties.getDistribution().getPercentiles().put("all", new double[] {0.5, 0.95 });
        properties.getDistribution().getSlo().put("http.server.requests", new ServiceLevelObjectiveBoundary[] {
                ServiceLevelObjectiveBoundary.valueOf("100ms"), ServiceLevelObjectiveBoundary.valueOf("250ms") });
        properties.getDistribution().getMinimumExpectedValue().put("http.server.requests", "10ms");
        properties.getDistribution().getMaximumExpectedValue().put("http.server.requests", "2s");
        properties.getDistribution().getExpiry().put("all", Duration.ofSeconds(30));
        properties.getDistribution().getBufferLength().put("all", 4);

        PropertiesMeterFilter filter = new PropertiesMeterFilter(properties);
        Meter.Id requests = meterId("http.server.requests", Meter.Type.TIMER, Tags.of("method", "GET"));
        Meter.Id errors = meterId("http.server.errors", Meter.Type.TIMER, Tags.empty());

        assertThat(filter.accept(requests)).isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(errors)).isEqualTo(MeterFilterReply.DENY);
        Meter.Id mapped = filter.map(requests);
        assertThat(mapped.getTag("application")).isEqualTo("store");
        assertThat(mapped.getTag("cluster")).isEqualTo("blue");

        DistributionStatisticConfig config = filter.configure(requests, DistributionStatisticConfig.DEFAULT);
        assertThat(config.isPercentileHistogram()).isTrue();
        assertThat(config.getPercentiles()).containsExactly(0.5, 0.95);
        assertThat(config.getServiceLevelObjectiveBoundaries()).containsExactly(100_000_000.0, 250_000_000.0);
        assertThat(config.getMinimumExpectedValueAsDouble()).isEqualTo(10_000_000.0);
        assertThat(config.getMaximumExpectedValueAsDouble()).isEqualTo(2_000_000_000.0);
        assertThat(config.getExpiry()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getBufferLength()).isEqualTo(4);
    }

    @Test
    void meterValuesConvertNumbersAndDurationsForSupportedMeterTypes() {
        MeterValue numericValue = MeterValue.valueOf("250");
        MeterValue durationValue = MeterValue.valueOf("250ms");
        ServiceLevelObjectiveBoundary numericBoundary = ServiceLevelObjectiveBoundary.valueOf(7.0);

        assertThat(numericValue.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isEqualTo(250.0);
        assertThat(numericValue.getValue(Meter.Type.TIMER)).isEqualTo((double) TimeUnit.MILLISECONDS.toNanos(250));
        assertThat(durationValue.getValue(Meter.Type.TIMER)).isEqualTo((double) TimeUnit.MILLISECONDS.toNanos(250));
        assertThat(durationValue.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isNull();
        assertThat(numericBoundary.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isEqualTo(7.0);
        assertThat(numericBoundary.getValue(Meter.Type.COUNTER)).isNull();
    }

    @Test
    void maximumAllowableTagsFilterDeniesNewTagValuesAfterLimitIsReached() {
        MaximumAllowableTagsMeterFilter filter = new MaximumAllowableTagsMeterFilter("http.server.requests", "uri", 2);

        assertThat(filter.accept(meterId("http.server.requests", Meter.Type.TIMER, Tags.of("uri", "/a"))))
                .isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(meterId("http.server.requests", Meter.Type.TIMER, Tags.of("uri", "/b"))))
                .isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(meterId("http.server.requests", Meter.Type.TIMER, Tags.of("uri", "/c"))))
                .isEqualTo(MeterFilterReply.DENY);
        assertThat(filter.accept(meterId("http.server.requests", Meter.Type.TIMER, Tags.of("uri", "/a"))))
                .isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(meterId("db.requests", Meter.Type.TIMER, Tags.of("uri", "/d"))))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    void simplePropertiesAdapterExposesConfiguredStepAndCountingMode() {
        SimpleProperties properties = new SimpleProperties();
        properties.setStep(Duration.ofSeconds(7));
        properties.setMode(CountingMode.STEP);
        properties.setEnabled(false);

        SimplePropertiesConfigAdapter adapter = new SimplePropertiesConfigAdapter(properties);

        assertThat(adapter.prefix()).isEqualTo("management.simple.metrics.export");
        assertThat(adapter.step()).isEqualTo(Duration.ofSeconds(7));
        assertThat(adapter.mode()).isEqualTo(CountingMode.STEP);
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void datadogPropertiesExposePushExportAndBackendSettings() {
        DatadogProperties properties = new DatadogProperties();
        properties.setStep(Duration.ofSeconds(12));
        properties.setEnabled(false);
        properties.setBatchSize(512);
        properties.setConnectTimeout(Duration.ofMillis(250));
        properties.setReadTimeout(Duration.ofSeconds(3));
        properties.setApiKey("test-api-key");
        properties.setApplicationKey("test-application-key");
        properties.setDescriptions(false);
        properties.setHostTag("node");
        properties.setUri("https://metrics.example.invalid");

        TestDatadogPushConfigAdapter adapter = new TestDatadogPushConfigAdapter(properties);

        assertThat(adapter.prefix()).isEqualTo("management.datadog.metrics.export");
        assertThat(adapter.get("custom.property")).isNull();
        assertThat(adapter.step()).isEqualTo(Duration.ofSeconds(12));
        assertThat(adapter.enabled()).isFalse();
        assertThat(adapter.batchSize()).isEqualTo(512);
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getApiKey()).isEqualTo("test-api-key");
        assertThat(properties.getApplicationKey()).isEqualTo("test-application-key");
        assertThat(properties.isDescriptions()).isFalse();
        assertThat(properties.getHostTag()).isEqualTo("node");
        assertThat(properties.getUri()).isEqualTo("https://metrics.example.invalid");
    }

    @Test
    void startupTimeMetricsListenerRegistersStartedAndReadyTimeGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GenericApplicationContext context = new GenericApplicationContext();
        try {
            SpringApplication application = new SpringApplication(SpringBootMicrometerMetricsTest.class);
            application.setMainApplicationClass(SpringBootMicrometerMetricsTest.class);
            StartupTimeMetricsListener listener = new StartupTimeMetricsListener(registry,
                    "test.application.started.time", "test.application.ready.time", Tags.of("phase", "test"));

            listener.onApplicationEvent(new ApplicationStartedEvent(application, new String[0], context,
                    Duration.ofMillis(1250)));
            listener.onApplicationEvent(new ApplicationReadyEvent(application, new String[0], context,
                    Duration.ofMillis(2500)));

            Meter started = registry.get("test.application.started.time")
                    .tag("phase", "test")
                    .tag("main.application.class", SpringBootMicrometerMetricsTest.class.getName())
                    .meter();
            Meter ready = registry.get("test.application.ready.time")
                    .tag("phase", "test")
                    .tag("main.application.class", SpringBootMicrometerMetricsTest.class.getName())
                    .meter();
            assertThat(started.getId().getDescription()).isEqualTo("Time taken to start the application");
            assertThat(ready.getId().getDescription())
                    .isEqualTo("Time taken for the application to be ready to service requests");
            assertThat(measurementValue(started, Statistic.VALUE)).isGreaterThan(0.0);
            assertThat(measurementValue(ready, Statistic.VALUE))
                    .isGreaterThan(measurementValue(started, Statistic.VALUE));
            assertThat(listener.supportsEventType(ApplicationStartedEvent.class)).isTrue();
            assertThat(listener.supportsEventType(ApplicationReadyEvent.class)).isTrue();
        } finally {
            context.close();
            registry.close();
        }
    }

    @Test
    void diskSpaceBinderRegistersTaggedDiskGaugesForConfiguredPaths() throws IOException {
        Path directory = Files.createTempDirectory("spring-boot-metrics");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new DiskSpaceMetricsBinder(List.of(directory.toFile()), Tags.of("volume", "test-volume")).bindTo(registry);

            assertThat(registry.find("disk.free").tag("volume", "test-volume").gauge()).isNotNull();
            assertThat(registry.find("disk.total").tag("volume", "test-volume").gauge()).isNotNull();
            assertThat(registry.get("disk.free").tag("volume", "test-volume").gauge().value())
                    .isGreaterThanOrEqualTo(0.0);
            assertThat(registry.get("disk.total").tag("volume", "test-volume").gauge().value())
                    .isGreaterThan(0.0);
        } finally {
            registry.close();
            Files.deleteIfExists(directory);
        }
    }

    private static Meter.Id meterId(String name, Meter.Type type, Tags tags) {
        return new Meter.Id(name, tags, null, null, type);
    }

    private static Double measurementValue(MetricDescriptor descriptor, Statistic statistic) {
        return descriptor.getMeasurements().stream()
                .filter((sample) -> sample.getStatistic() == statistic)
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    private static Double measurementValue(Meter meter, Statistic statistic) {
        for (Measurement measurement : meter.measure()) {
            if (measurement.getStatistic() == statistic) {
                return measurement.getValue();
            }
        }
        throw new IllegalStateException("No measurement found for " + statistic);
    }

    private static Set<String> valuesForTag(MetricDescriptor descriptor, String tag) {
        return descriptor.getAvailableTags().stream()
                .filter((availableTag) -> availableTag.getTag().equals(tag))
                .findFirst()
                .orElseThrow()
                .getValues();
    }

    private static final class TestDatadogPushConfigAdapter
            extends PushRegistryPropertiesConfigAdapter<DatadogProperties> {

        TestDatadogPushConfigAdapter(DatadogProperties properties) {
            super(properties);
        }

        @Override
        public String prefix() {
            return "management.datadog.metrics.export";
        }

    }

}
