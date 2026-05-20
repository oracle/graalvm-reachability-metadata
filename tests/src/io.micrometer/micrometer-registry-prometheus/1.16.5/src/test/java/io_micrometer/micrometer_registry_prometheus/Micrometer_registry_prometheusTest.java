/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_registry_prometheus;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusRenameFilter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Micrometer_registry_prometheusTest {
    private static final String TEXT_FORMAT = "text/plain; version=0.0.4; charset=utf-8";

    @Test
    void recordsCommonMeterTypesAndScrapesThemInPrometheusText() {
        PrometheusMeterRegistry registry = newRegistry();
        AtomicInteger queueDepth = new AtomicInteger(7);
        AtomicLong evictions = new AtomicLong(4);
        long[] cacheLoadStats = { 3, 250 };

        Counter createdOrders = Counter.builder("orders.created")
                .description("Orders created by checkout")
                .tag("status", "created")
                .register(registry);
        Timer requests = Timer.builder("http.server.requests")
                .description("HTTP request latency")
                .tag("method", "GET")
                .tag("status", "200")
                .register(registry);
        DistributionSummary payloadSize = DistributionSummary.builder("payload.size")
                .baseUnit("bytes")
                .tag("endpoint", "bulk")
                .register(registry);
        Gauge queueGauge = Gauge.builder("queue.depth", queueDepth, AtomicInteger::get)
                .tag("queue", "orders")
                .register(registry);
        FunctionCounter cacheEvictions = FunctionCounter.builder("cache.evictions", evictions, AtomicLong::get)
                .tag("cache", "products")
                .register(registry);
        FunctionTimer cacheLoad = FunctionTimer
                .builder("cache.load", cacheLoadStats, stats -> stats[0], stats -> stats[1], TimeUnit.MILLISECONDS)
                .tag("cache", "products")
                .register(registry);

        createdOrders.increment(3);
        requests.record(Duration.ofMillis(125));
        payloadSize.record(64);
        payloadSize.record(128);

        assertThat(createdOrders.count()).isEqualTo(3.0);
        assertThat(requests.count()).isEqualTo(1);
        assertThat(payloadSize.count()).isEqualTo(2);
        assertThat(queueGauge.value()).isEqualTo(7.0);
        assertThat(cacheEvictions.count()).isEqualTo(4.0);
        assertThat(cacheLoad.count()).isEqualTo(3.0);

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("# HELP orders_total Orders created by checkout")
                .contains("# TYPE orders_total counter")
                .contains("orders_total")
                .contains("status=\"created\"")
                .contains("http_server_requests_seconds_count")
                .contains("http_server_requests_seconds_sum")
                .contains("method=\"GET\"")
                .contains("status=\"200\"")
                .contains("payload_size_bytes_count")
                .contains("payload_size_bytes_sum")
                .contains("endpoint=\"bulk\"")
                .contains("queue_depth")
                .contains("queue=\"orders\"")
                .contains("cache_evictions_total")
                .contains("cache_load_seconds_count")
                .contains("cache_load_seconds_sum")
                .contains("cache=\"products\"");
    }

    @Test
    void percentileHistogramsExposeBucketsCountsAndSums() {
        PrometheusMeterRegistry registry = newRegistry();
        Timer histogramTimer = Timer.builder("request.latency")
                .description("Request latency distribution")
                .tag("route", "/orders/{id}")
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250))
                .register(registry);
        Timer percentileTimer = Timer.builder("request.percentile")
                .tag("route", "/orders/{id}")
                .publishPercentiles(0.5, 0.95)
                .register(registry);

        histogramTimer.record(Duration.ofMillis(25));
        histogramTimer.record(Duration.ofMillis(75));
        histogramTimer.record(Duration.ofMillis(300));
        percentileTimer.record(Duration.ofMillis(25));
        percentileTimer.record(Duration.ofMillis(75));
        percentileTimer.record(Duration.ofMillis(300));

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("request_latency_seconds_bucket")
                .contains("request_latency_seconds_count")
                .contains("request_latency_seconds_sum")
                .contains("request_percentile_seconds")
                .contains("route=\"/orders/{id}\"")
                .contains("le=\"")
                .contains("quantile=\"0.5\"")
                .contains("quantile=\"0.95\"");
    }

    @Test
    void multiGaugeRowsAreRegisteredWithCommonAndRowTags() {
        PrometheusMeterRegistry registry = newRegistry();
        MultiGauge statuses = MultiGauge.builder("queue.items")
                .description("Items by queue state")
                .tag("queue", "orders")
                .register(registry);

        statuses.register(List.of(
                MultiGauge.Row.of(Tags.of("state", "ready"), 5),
                MultiGauge.Row.of(Tags.of("state", "delayed"), 2)), true);

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("queue_items")
                .contains("queue=\"orders\"")
                .contains("state=\"ready\"")
                .contains("state=\"delayed\"");
    }

    @Test
    void writesScrapeToOutputStreamsAndCanFilterByMetricName() throws IOException {
        PrometheusMeterRegistry registry = newRegistry();
        Counter.builder("filtered.included").register(registry).increment();
        Counter.builder("filtered.excluded").register(registry).increment();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        registry.scrape(output);
        String completeScrape = output.toString(StandardCharsets.UTF_8.name());
        assertThat(completeScrape)
                .contains("filtered_included_total")
                .contains("filtered_excluded_total");

        String filteredScrape = registry.scrape(TEXT_FORMAT, Set.of("filtered_included"));
        assertThat(filteredScrape)
                .contains("filtered_included_total")
                .doesNotContain("filtered_excluded_total");
    }

    @Test
    void removingMeterUnregistersCollectorAndAllowsNameToBeReusedByAnotherType() {
        PrometheusMeterRegistry registry = newRegistry();
        Counter counter = Counter.builder("temporary.events")
                .tag("phase", "before")
                .register(registry);
        counter.increment();
        assertThat(registry.scrape()).contains("temporary_events_total");

        Meter removedMeter = registry.remove(counter);
        assertThat(removedMeter).isSameAs(counter);
        assertThat(registry.scrape()).doesNotContain("temporary_events_total");

        AtomicInteger gaugeValue = new AtomicInteger(9);
        Gauge.builder("temporary.events", gaugeValue, AtomicInteger::get)
                .tag("phase", "after")
                .register(registry);

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("temporary_events")
                .contains("phase=\"after\"")
                .doesNotContain("temporary_events_total")
                .doesNotContain("phase=\"before\"");
    }

    @Test
    void registrationFailureCanBeConfiguredToThrowForIncompatiblePrometheusNames() {
        PrometheusMeterRegistry registry = newRegistry().throwExceptionOnRegistrationFailure();
        Counter.builder("conflicting.metric")
                .tag("kind", "counter")
                .register(registry);

        AtomicInteger gaugeValue = new AtomicInteger(1);
        assertThatThrownBy(() -> Gauge.builder("conflicting.metric", gaugeValue, AtomicInteger::get)
                .tag("kind", "gauge")
                .register(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same name")
                .hasMessageContaining("same type");
    }

    @Test
    void prometheusRenameFilterConvertsKnownJvmMetricNames() {
        PrometheusMeterRegistry registry = newRegistry();
        registry.config().meterFilter(new PrometheusRenameFilter());

        AtomicInteger openFiles = new AtomicInteger(17);
        Gauge.builder("process.files.open", openFiles, AtomicInteger::get)
                .register(registry);
        Gauge.builder("process.start.time", () -> 1_234.0)
                .register(registry);

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("process_open_fds")
                .contains("# HELP process_start_time Start time of the process since unix epoch in seconds.")
                .contains("process_start_time")
                .doesNotContain("process_files_open");
    }

    @Test
    void customConfigurationAndPrometheusRegistryAreUsed() {
        PrometheusRegistry prometheusRegistry = new PrometheusRegistry();
        PrometheusConfig configWithoutDescriptions = new PrometheusConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public boolean descriptions() {
                return false;
            }
        };
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(
                configWithoutDescriptions, prometheusRegistry, Clock.SYSTEM);

        assertThat(registry.getPrometheusRegistry()).isSameAs(prometheusRegistry);
        Counter.builder("config.description")
                .description("Description intentionally omitted from scrape output")
                .register(registry)
                .increment();

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("config_description_total")
                .doesNotContain("Description intentionally omitted from scrape output");
    }

    private static PrometheusMeterRegistry newRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}
