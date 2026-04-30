/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_dropwizard_metrics.metrics_json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.json.HealthCheckModule;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Metrics_jsonTest {
    @Test
    void moduleExposesIdentityAndSerializesIndividualMetricTypes() throws Exception {
        MetricsModule module = new MetricsModule(TimeUnit.SECONDS, TimeUnit.MILLISECONDS, true);
        ObjectMapper mapper = mapperWith(module);

        Counter counter = new Counter();
        counter.inc(7);

        Gauge<Integer> gauge = () -> 42;
        Gauge<Integer> failingGauge = () -> {
            throw new IllegalStateException("sensor unavailable");
        };

        Histogram histogram = new Histogram(new SlidingWindowReservoir(10));
        histogram.update(10);
        histogram.update(20);
        histogram.update(30);

        Meter meter = new Meter();
        meter.mark(3);

        Timer timer = new Timer(new SlidingWindowReservoir(10));
        timer.update(1500, TimeUnit.MICROSECONDS);
        timer.update(2, TimeUnit.MILLISECONDS);

        JsonNode counterJson = toJson(mapper, counter);
        JsonNode gaugeJson = toJson(mapper, gauge);
        JsonNode failingGaugeJson = toJson(mapper, failingGauge);
        JsonNode histogramJson = toJson(mapper, histogram);
        JsonNode meterJson = toJson(mapper, meter);
        JsonNode timerJson = toJson(mapper, timer);

        assertThat(module.getModuleName()).isEqualTo("metrics");
        assertThat(module.version().getGroupId()).isEqualTo("io.dropwizard.metrics");
        assertThat(module.version().getArtifactId()).isEqualTo("metrics-json");
        assertThat(module.version().isUnknownVersion()).isFalse();

        assertThat(counterJson.get("count").asLong()).isEqualTo(7L);
        assertThat(gaugeJson.get("value").asInt()).isEqualTo(42);
        assertThat(failingGaugeJson.get("error").asText())
                .isEqualTo("java.lang.IllegalStateException: sensor unavailable");

        assertThat(histogramJson.get("count").asLong()).isEqualTo(3L);
        assertThat(histogramJson.get("min").asLong()).isEqualTo(10L);
        assertThat(histogramJson.get("max").asLong()).isEqualTo(30L);
        assertThat(longValues(histogramJson.get("values"))).containsExactly(10L, 20L, 30L);
        assertThat(histogramJson.get("p50").asDouble()).isEqualTo(20.0d);
        assertThat(histogramJson.get("stddev").asDouble()).isGreaterThan(0.0d);

        assertThat(meterJson.get("count").asLong()).isEqualTo(3L);
        assertThat(meterJson.get("units").asText()).isEqualTo("events/second");
        assertThat(meterJson.has("mean_rate")).isTrue();
        assertThat(meterJson.has("m1_rate")).isTrue();
        assertThat(meterJson.has("m5_rate")).isTrue();
        assertThat(meterJson.has("m15_rate")).isTrue();

        assertThat(timerJson.get("count").asLong()).isEqualTo(2L);
        assertThat(timerJson.get("min").asDouble()).isEqualTo(1.5d);
        assertThat(timerJson.get("max").asDouble()).isEqualTo(2.0d);
        assertThat(doubleValues(timerJson.get("values"))).containsExactly(1.5d, 2.0d);
        assertThat(timerJson.get("duration_units").asText()).isEqualTo("milliseconds");
        assertThat(timerJson.get("rate_units").asText()).isEqualTo("calls/second");
    }

    @Test
    void registrySerializerGroupsMetricsAndAppliesTheConfiguredFilter() throws Exception {
        MetricFilter apiOnlyFilter = (String name, Metric metric) -> name.startsWith("api.");
        ObjectMapper mapper = mapperWith(new MetricsModule(TimeUnit.SECONDS, TimeUnit.MICROSECONDS, true,
                apiOnlyFilter));
        MetricRegistry registry = new MetricRegistry();

        registry.register("api.queue.depth", (Gauge<Integer>) () -> 5);
        registry.counter("api.requests").inc(11);
        registry.histogram("api.payload.bytes").update(512);
        registry.meter("api.throughput").mark(2);
        registry.timer("api.latency").update(750, TimeUnit.MICROSECONDS);
        registry.counter("internal.requests").inc(99);
        registry.register("internal.queue.depth", (Gauge<Integer>) () -> 100);

        JsonNode root = toJson(mapper, registry);

        assertThat(root.get("version").asText()).isEqualTo("4.0.0");
        assertThat(fieldNames(root.get("gauges"))).containsExactly("api.queue.depth");
        assertThat(fieldNames(root.get("counters"))).containsExactly("api.requests");
        assertThat(fieldNames(root.get("histograms"))).containsExactly("api.payload.bytes");
        assertThat(fieldNames(root.get("meters"))).containsExactly("api.throughput");
        assertThat(fieldNames(root.get("timers"))).containsExactly("api.latency");

        assertThat(root.at("/gauges/api.queue.depth/value").asInt()).isEqualTo(5);
        assertThat(root.at("/counters/api.requests/count").asLong()).isEqualTo(11L);
        assertThat(root.at("/histograms/api.payload.bytes/count").asLong()).isEqualTo(1L);
        assertThat(root.at("/meters/api.throughput/count").asLong()).isEqualTo(2L);
        assertThat(root.at("/timers/api.latency/count").asLong()).isEqualTo(1L);
        assertThat(root.at("/timers/api.latency/min").asDouble()).isEqualTo(750.0d);
        assertThat(root.at("/timers/api.latency/duration_units").asText()).isEqualTo("microseconds");
    }

    @Test
    void meterRatesAreConvertedToTheConfiguredRateUnit() throws Exception {
        ManualClock clock = new ManualClock();
        Meter meter = new Meter(clock);
        ObjectMapper mapper = mapperWith(new MetricsModule(TimeUnit.MINUTES, TimeUnit.MILLISECONDS, true));

        meter.mark(4);
        clock.add(1, TimeUnit.SECONDS);

        JsonNode json = toJson(mapper, meter);

        assertThat(json.get("count").asLong()).isEqualTo(4L);
        assertThat(json.get("mean_rate").asDouble()).isEqualTo(240.0d);
        assertThat(json.get("units").asText()).isEqualTo("events/minute");
    }

    @Test
    void sampleArraysCanBeSuppressedWithoutRemovingDistributionStatistics() throws Exception {
        ObjectMapper mapper = mapperWith(new MetricsModule(TimeUnit.SECONDS, TimeUnit.NANOSECONDS, false));
        Histogram histogram = new Histogram(new SlidingWindowReservoir(10));
        Timer timer = new Timer(new SlidingWindowReservoir(10));

        histogram.update(4);
        histogram.update(8);
        timer.update(12, TimeUnit.NANOSECONDS);
        timer.update(16, TimeUnit.NANOSECONDS);

        JsonNode histogramJson = toJson(mapper, histogram);
        JsonNode timerJson = toJson(mapper, timer);

        assertThat(histogramJson.has("values")).isFalse();
        assertThat(histogramJson.get("count").asLong()).isEqualTo(2L);
        assertThat(histogramJson.get("min").asLong()).isEqualTo(4L);
        assertThat(histogramJson.get("max").asLong()).isEqualTo(8L);
        assertThat(histogramJson.get("p50").asDouble()).isEqualTo(6.0d);

        assertThat(timerJson.has("values")).isFalse();
        assertThat(timerJson.get("count").asLong()).isEqualTo(2L);
        assertThat(timerJson.get("min").asDouble()).isEqualTo(12.0d);
        assertThat(timerJson.get("max").asDouble()).isEqualTo(16.0d);
        assertThat(timerJson.get("p50").asDouble()).isEqualTo(14.0d);
        assertThat(timerJson.get("duration_units").asText()).isEqualTo("nanoseconds");
    }

    @Test
    void healthCheckModuleSerializesResultStatusDetailsAndNestedErrors() throws Exception {
        HealthCheckModule module = new HealthCheckModule();
        ObjectMapper mapper = mapperWith(module);
        IllegalStateException cause = new IllegalStateException("connection refused");
        IllegalArgumentException error = new IllegalArgumentException("database unavailable", cause);
        HealthCheck.Result result = HealthCheck.Result.builder()
                .unhealthy(error)
                .withMessage("readiness check failed")
                .withDetail("service", "orders")
                .withDetail("attempt", 3)
                .build();

        JsonNode json = toJson(mapper, result);

        assertThat(module.getModuleName()).isEqualTo("healthchecks");
        assertThat(module.version().getGroupId()).isEqualTo("io.dropwizard.metrics");
        assertThat(module.version().getArtifactId()).isEqualTo("metrics-json");

        assertThat(json.get("healthy").asBoolean()).isFalse();
        assertThat(json.get("message").asText()).isEqualTo("readiness check failed");
        assertThat(json.get("service").asText()).isEqualTo("orders");
        assertThat(json.get("attempt").asInt()).isEqualTo(3);
        assertThat(json.get("duration").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.at("/error/type").asText()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(json.at("/error/message").asText()).isEqualTo("database unavailable");
        assertThat(json.at("/error/stack").isArray()).isTrue();
        assertThat(json.at("/error/stack").size()).isGreaterThan(0);
        assertThat(json.at("/error/cause/type").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(json.at("/error/cause/message").asText()).isEqualTo("connection refused");
    }

    private static final class ManualClock extends Clock {
        private long tick;

        @Override
        public long getTick() {
            return tick;
        }

        private void add(long duration, TimeUnit unit) {
            tick += unit.toNanos(duration);
        }
    }

    private static ObjectMapper mapperWith(MetricsModule module) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        return mapper;
    }

    private static ObjectMapper mapperWith(HealthCheckModule module) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        return mapper;
    }

    private static JsonNode toJson(ObjectMapper mapper, Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(value));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return names;
    }

    private static List<Long> longValues(JsonNode node) {
        List<Long> values = new ArrayList<>();
        for (JsonNode value : node) {
            values.add(value.asLong());
        }
        return values;
    }

    private static List<Double> doubleValues(JsonNode node) {
        List<Double> values = new ArrayList<>();
        for (JsonNode value : node) {
            values.add(value.asDouble());
        }
        return values;
    }
}
