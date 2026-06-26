/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_metrics;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.micrometer.metrics.MaximumAllowableTagsMeterFilter;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.AvailableTag;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.MetricDescriptor;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.Sample;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterValue;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.PropertiesMeterFilter;
import org.springframework.boot.micrometer.metrics.autoconfigure.ServiceLevelObjectiveBoundary;
import org.springframework.boot.micrometer.metrics.startup.StartupTimeMetricsListener;
import org.springframework.boot.micrometer.metrics.system.DiskSpaceMetricsBinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_boot_micrometer_metricsTest {

    @TempDir
    private Path tempDir;

    @Test
    void maximumAllowableTagsFilterDeniesNewTagValuesAfterLimit() {
        MaximumAllowableTagsMeterFilter filter = new MaximumAllowableTagsMeterFilter("http.server.requests", "uri",
                2, "Use templated URIs.");

        assertThat(filter.accept(id("http.server.requests", Meter.Type.COUNTER, Tags.of("uri", "/one"))))
                .isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(id("http.server.requests", Meter.Type.COUNTER, Tags.of("uri", "/two"))))
                .isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(id("http.server.requests", Meter.Type.COUNTER, Tags.of("uri", "/one"))))
                .isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(id("http.server.requests", Meter.Type.COUNTER, Tags.of("uri", "/three"))))
                .isEqualTo(MeterFilterReply.DENY);
        assertThat(filter.accept(id("http.client.requests", Meter.Type.COUNTER, Tags.of("uri", "/outside"))))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    void propertiesMeterFilterAppliesCommonTagsAndEnablementByMostSpecificName() {
        MetricsProperties properties = new MetricsProperties();
        properties.getTags().put("application", "native-metadata-test");
        properties.getTags().put("cluster", "test");
        properties.getEnable().put("all", false);
        properties.getEnable().put("http.server", true);
        properties.getEnable().put("http.server.requests", false);
        PropertiesMeterFilter filter = new PropertiesMeterFilter(properties);

        Meter.Id serverRequests = id("http.server.requests", Meter.Type.TIMER, Tags.of("method", "GET"));
        Meter.Id serverActive = id("http.server.active", Meter.Type.GAUGE, Tags.empty());
        Meter.Id unrelated = id("cache.gets", Meter.Type.COUNTER, Tags.empty());

        assertThat(filter.accept(serverRequests)).isEqualTo(MeterFilterReply.DENY);
        assertThat(filter.accept(serverActive)).isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(unrelated)).isEqualTo(MeterFilterReply.DENY);
        assertThat(filter.map(serverActive).getTags()).contains(Tag.of("application", "native-metadata-test"),
                Tag.of("cluster", "test"));
    }

    @Test
    void propertiesMeterFilterConfiguresDistributionStatisticsForMatchingMeters() {
        MetricsProperties properties = new MetricsProperties();
        MetricsProperties.Distribution distribution = properties.getDistribution();
        distribution.getPercentilesHistogram().put("all", false);
        distribution.getPercentilesHistogram().put("http.server", true);
        distribution.getPercentiles().put("http.server.requests", new double[] { 0.5, 0.95 });
        distribution.getSlo()
                .put("http.server.requests",
                        new ServiceLevelObjectiveBoundary[] { ServiceLevelObjectiveBoundary.valueOf("100ms"),
                                ServiceLevelObjectiveBoundary.valueOf("250ms") });
        distribution.getMinimumExpectedValue().put("http.server.requests", "10ms");
        distribution.getMaximumExpectedValue().put("http.server.requests", "2s");
        distribution.getExpiry().put("http.server", Duration.ofSeconds(30));
        distribution.getBufferLength().put("http.server", 4);
        PropertiesMeterFilter filter = new PropertiesMeterFilter(properties);

        Meter.Id requests = id("http.server.requests", Meter.Type.TIMER, Tags.empty());
        DistributionStatisticConfig config = filter.configure(requests, DistributionStatisticConfig.DEFAULT);

        assertThat(config.isPercentileHistogram()).isTrue();
        assertThat(config.getPercentiles()).containsExactly(0.5, 0.95);
        assertThat(config.getServiceLevelObjectiveBoundaries()).containsExactly(100_000_000.0, 250_000_000.0);
        assertThat(config.getMinimumExpectedValue()).isEqualTo(10_000_000.0);
        assertThat(config.getMaximumExpectedValue()).isEqualTo(2_000_000_000.0);
        assertThat(config.getExpiry()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getBufferLength()).isEqualTo(4);
    }

    @Test
    void meterValuesAndServiceLevelObjectivesConvertByMeterType() {
        MeterValue numeric = MeterValue.valueOf("42");
        MeterValue duration = MeterValue.valueOf("125ms");
        ServiceLevelObjectiveBoundary numericBoundary = ServiceLevelObjectiveBoundary.valueOf(3.5);
        ServiceLevelObjectiveBoundary durationBoundary = ServiceLevelObjectiveBoundary.valueOf("2s");

        assertThat(numeric.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isEqualTo(42.0);
        assertThat(numeric.getValue(Meter.Type.TIMER)).isEqualTo(42_000_000.0);
        assertThat(duration.getValue(Meter.Type.TIMER)).isEqualTo(125_000_000.0);
        assertThat(duration.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isNull();
        assertThat(numericBoundary.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isEqualTo(3.5);
        assertThat(durationBoundary.getValue(Meter.Type.TIMER)).isEqualTo(2_000_000_000.0);
        assertThat(durationBoundary.getValue(Meter.Type.COUNTER)).isNull();
    }

    @Test
    void diskSpaceBinderRegistersMetersForEveryConfiguredPath() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            File firstPath = this.tempDir.toFile();
            File secondPath = this.tempDir.getParent().toFile();
            DiskSpaceMetricsBinder binder = new DiskSpaceMetricsBinder(List.of(firstPath, secondPath),
                    Tags.of("scope", "filesystem"));

            binder.bindTo(registry);

            List<Tag> tags = registry.getMeters()
                .stream()
                .flatMap((meter) -> meter.getId().getTags().stream())
                .toList();
            assertThat(registry.find("disk.free").tag("scope", "filesystem").gauges()).hasSize(2);
            assertThat(registry.find("disk.total").tag("scope", "filesystem").gauges()).hasSize(2);
            assertThat(tags).contains(Tag.of("path", firstPath.getAbsolutePath()),
                    Tag.of("path", secondPath.getAbsolutePath()));
        }
        finally {
            registry.close();
        }
    }

    @Test
    void startupTimeMetricsListenerRegistersStartedAndReadyGaugesWithApplicationTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            StartupTimeMetricsListener listener = new StartupTimeMetricsListener(registry, "test.application.started",
                    "test.application.ready", Tags.of("deployment", "test"));
            SpringApplication application = new SpringApplication(Spring_boot_micrometer_metricsTest.class);
            application.setMainApplicationClass(Spring_boot_micrometer_metricsTest.class);

            assertThat(listener.supportsEventType(ApplicationStartedEvent.class)).isTrue();
            assertThat(listener.supportsEventType(ApplicationReadyEvent.class)).isTrue();

            listener.onApplicationEvent(new ApplicationStartedEvent(application, new String[0], null,
                    Duration.ofMillis(1500)));
            listener.onApplicationEvent(new ApplicationReadyEvent(application, new String[0], null,
                    Duration.ofMillis(2750)));

            String mainApplicationClass = Spring_boot_micrometer_metricsTest.class.getName();
            TimeGauge started = registry.get("test.application.started")
                .tag("deployment", "test")
                .tag("main.application.class", mainApplicationClass)
                .timeGauge();
            TimeGauge ready = registry.get("test.application.ready")
                .tag("deployment", "test")
                .tag("main.application.class", mainApplicationClass)
                .timeGauge();

            assertThat(started.getId().getDescription()).isEqualTo("Time taken to start the application");
            assertThat(started.value(TimeUnit.MILLISECONDS)).isEqualTo(1500.0);
            assertThat(ready.getId().getDescription())
                .isEqualTo("Time taken for the application to be ready to service requests");
            assertThat(ready.value(TimeUnit.MILLISECONDS)).isEqualTo(2750.0);
        }
        finally {
            registry.close();
        }
    }

    @Test
    void metricsEndpointListsFiltersAndDescribesMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            Counter.builder("library.requests")
                .description("Requests handled by the library")
                .baseUnit("requests")
                .tag("region", "us-east")
                .tag("status", "ok")
                .register(registry)
                .increment(2.0);
            Counter.builder("library.requests")
                .description("Requests handled by the library")
                .baseUnit("requests")
                .tag("region", "us-east")
                .tag("status", "failed")
                .register(registry)
                .increment(3.0);
            Counter.builder("library.background").register(registry).increment();
            MetricsEndpoint endpoint = new MetricsEndpoint(registry);

            assertThat(endpoint.listNames().getNames()).containsExactly("library.background", "library.requests");

            MetricDescriptor allRequests = endpoint.metric("library.requests", null);
            assertThat(allRequests).isNotNull();
            assertThat(allRequests.getName()).isEqualTo("library.requests");
            assertThat(allRequests.getDescription()).isEqualTo("Requests handled by the library");
            assertThat(allRequests.getBaseUnit()).isEqualTo("requests");
            assertThat(samplesByStatistic(allRequests)).containsEntry(Statistic.COUNT, 5.0);
            assertThat(availableTagsByName(allRequests)).containsEntry("region", Set.of("us-east"))
                .containsEntry("status", Set.of("ok", "failed"));

            MetricDescriptor successfulRequests = endpoint.metric("library.requests", List.of("status:ok"));
            assertThat(successfulRequests).isNotNull();
            assertThat(samplesByStatistic(successfulRequests)).containsEntry(Statistic.COUNT, 2.0);
            assertThat(availableTagsByName(successfulRequests)).containsEntry("region", Set.of("us-east"))
                .doesNotContainKey("status");
            assertThat(endpoint.metric("library.requests", List.of("status:missing"))).isNull();
            assertThat(endpoint.metric("missing", null)).isNull();
            assertThatThrownBy(() -> endpoint.metric("library.requests", List.of("status")))
                    .isInstanceOf(InvalidEndpointRequestException.class)
                    .hasMessageContaining("Each tag parameter must be in the form 'key:value'");
        }
        finally {
            registry.close();
        }
    }

    private static Meter.Id id(String name, Meter.Type type, Tags tags) {
        return new Meter.Id(name, tags, null, null, type);
    }

    private static Map<Statistic, Double> samplesByStatistic(MetricDescriptor descriptor) {
        return descriptor.getMeasurements()
            .stream()
            .collect(Collectors.toMap(Sample::getStatistic, Sample::getValue));
    }

    private static Map<String, Set<String>> availableTagsByName(MetricDescriptor descriptor) {
        return descriptor.getAvailableTags()
            .stream()
            .collect(Collectors.toMap(AvailableTag::getTag, AvailableTag::getValues, (first, second) -> first));
    }

}
