/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_registry_otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.registry.otlp.AggregationTemporality;
import io.micrometer.registry.otlp.CompressionMode;
import io.micrometer.registry.otlp.HistogramFlavor;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.registry.otlp.OtlpMetricsSender;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class Micrometer_registry_otlpTest {
    @Test
    void exportsCommonMeterTypesAsOtlpProtobuf() throws Exception {
        CapturingMetricsSender sender = new CapturingMetricsSender();
        Map<String, String> resourceAttributes = Map.of("service.name", "checkout", "deployment.environment", "test");
        Map<String, String> headers = Map.of("Authorization", "Bearer test-token", "tenant", "orders");
        TestOtlpConfig config = new TestOtlpConfig("http://collector.example/v1/metrics", resourceAttributes, headers);

        AtomicInteger queueDepth = new AtomicInteger(7);
        AtomicLong evictions = new AtomicLong(4);
        long[] cacheLoadStats = {3, 250};

        OtlpMeterRegistry registry = registry(config, sender);
        try {
            Counter.builder("orders.created")
                    .description("Orders created by checkout")
                    .tag("status", "created")
                    .register(registry)
                    .increment(3);
            Timer.builder("http.server.requests")
                    .description("HTTP request latency")
                    .tag("method", "GET")
                    .tag("status", "200")
                    .publishPercentileHistogram()
                    .serviceLevelObjectives(Duration.ofMillis(50), Duration.ofMillis(100))
                    .register(registry)
                    .record(Duration.ofMillis(75));
            DistributionSummary.builder("payload.size")
                    .baseUnit("bytes")
                    .tag("endpoint", "bulk")
                    .register(registry)
                    .record(128);
            Gauge.builder("queue.depth", queueDepth, AtomicInteger::get)
                    .tag("queue", "orders")
                    .register(registry);
            FunctionCounter.builder("cache.evictions", evictions, AtomicLong::get)
                    .tag("cache", "products")
                    .register(registry);
            FunctionTimer.builder("cache.load", cacheLoadStats, stats -> stats[0], stats -> stats[1],
                            TimeUnit.MILLISECONDS)
                    .tag("cache", "products")
                    .register(registry);
        }
        finally {
            registry.close();
        }

        OtlpMetricsSender.Request sentRequest = sender.singleRequest();
        ExportMetricsServiceRequest request = exportRequest(sentRequest);
        assertThat(sentRequest.getAddress()).isEqualTo("http://collector.example/v1/metrics");
        assertThat(sentRequest.getHeaders()).containsAllEntriesOf(headers);
        assertThat(sentRequest.getCompressionMode()).isEqualTo(CompressionMode.NONE);

        assertThat(resourceAttributes(request))
                .containsEntry("service.name", "checkout")
                .containsEntry("deployment.environment", "test")
                .containsEntry("telemetry.sdk.language", "java")
                .containsKey("telemetry.sdk.name");

        Map<String, Metric> metrics = metricsByName(request);
        assertThat(metrics.keySet()).contains(
                "orders.created",
                "http.server.requests",
                "http.server.requests.max",
                "payload.size",
                "payload.size.max",
                "queue.depth",
                "cache.evictions",
                "cache.load");

        Metric counter = metrics.get("orders.created");
        assertThat(counter.hasSum()).isTrue();
        assertThat(counter.getSum().getIsMonotonic()).isTrue();
        assertThat(counter.getSum().getAggregationTemporality()).isEqualTo(
                io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
        assertThat(counter.getSum().getDataPoints(0).getAsDouble()).isEqualTo(3.0);
        assertThat(attributes(counter)).containsEntry("status", "created");

        Metric timer = metrics.get("http.server.requests");
        assertThat(timer.hasHistogram()).isTrue();
        assertThat(timer.getHistogram().getDataPoints(0).getCount()).isEqualTo(1L);
        assertThat(timer.getHistogram().getDataPoints(0).getSum()).isEqualTo(75.0);
        assertThat(attributes(timer)).containsEntry("method", "GET").containsEntry("status", "200");

        Metric gauge = metrics.get("queue.depth");
        assertThat(gauge.hasGauge()).isTrue();
        assertThat(gauge.getGauge().getDataPoints(0).getAsDouble()).isEqualTo(7.0);
        assertThat(attributes(gauge)).containsEntry("queue", "orders");
    }

    @Test
    void exportsDeltaTemporalityCompressionAndExponentialHistograms() throws Exception {
        CapturingMetricsSender sender = new CapturingMetricsSender();
        TestOtlpConfig config = new TestOtlpConfig("http://collector.example/metrics", Map.of(), Map.of()) {
            @Override
            public AggregationTemporality aggregationTemporality() {
                return AggregationTemporality.DELTA;
            }

            @Override
            public CompressionMode compressionMode() {
                return CompressionMode.GZIP;
            }

            @Override
            public HistogramFlavor histogramFlavor() {
                return HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
            }

            @Override
            public int maxBucketCount() {
                return 12;
            }
        };

        OtlpMeterRegistry registry = registry(config, sender);
        try {
            Counter.builder("delta.events").register(registry).increment(5);
            Timer.builder("delta.latency")
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Duration.ofMillis(20));
        }
        finally {
            registry.close();
        }

        OtlpMetricsSender.Request sentRequest = sender.singleRequest();
        Map<String, Metric> metrics = metricsByName(exportRequest(sentRequest));
        assertThat(sentRequest.getCompressionMode()).isEqualTo(CompressionMode.GZIP);
        assertThat(metrics.get("delta.events").getSum().getAggregationTemporality()).isEqualTo(
                io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA);
        assertThat(metrics.get("delta.events").getSum().getDataPointsList()).hasSize(1);

        Metric latency = metrics.get("delta.latency");
        assertThat(latency.hasExponentialHistogram()).isTrue();
        assertThat(latency.getExponentialHistogram().getAggregationTemporality()).isEqualTo(
                io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA);
        assertThat(latency.getExponentialHistogram().getDataPoints(0).getCount()).isEqualTo(1L);
    }

    @Test
    void longTaskTimersAndMeterRemovalAreReflectedAtPublishTime() throws Exception {
        CapturingMetricsSender sender = new CapturingMetricsSender();
        TestOtlpConfig config = new TestOtlpConfig("http://collector.example/v1/metrics", Map.of(), Map.of());
        AtomicInteger gaugeValue = new AtomicInteger(11);

        OtlpMeterRegistry registry = registry(config, sender);
        try {
            Counter temporary = Counter.builder("temporary.events")
                    .tag("phase", "before")
                    .register(registry);
            temporary.increment();
            Meter removed = registry.remove(temporary);
            assertThat(removed).isSameAs(temporary);

            Gauge.builder("temporary.events", gaugeValue, AtomicInteger::get)
                    .tag("phase", "after")
                    .register(registry);

            LongTaskTimer importTimer = LongTaskTimer.builder("batch.import")
                    .description("Running batch imports")
                    .tag("source", "catalog")
                    .register(registry);
            LongTaskTimer.Sample sample = importTimer.start();
            try {
                assertThat(importTimer.activeTasks()).isEqualTo(1);
                assertThat(importTimer.duration(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(0.0);
            }
            finally {
                sample.stop();
            }
        }
        finally {
            registry.close();
        }

        Map<String, Metric> metrics = metricsByName(exportRequest(sender.singleRequest()));
        assertThat(metrics.get("temporary.events").hasGauge()).isTrue();
        assertThat(attributes(metrics.get("temporary.events"))).containsEntry("phase", "after");
        assertThat(metrics.get("temporary.events").getGauge().getDataPoints(0).getAsDouble()).isEqualTo(11.0);
        assertThat(metrics.get("temporary.events").hasSum()).isFalse();
        assertThat(metrics.keySet()).contains("batch.import");
        assertThat(attributes(metrics.get("batch.import"))).containsEntry("source", "catalog");
    }

    @Test
    void requestBuilderPreservesPayloadMetadataAndReadableRepresentation() throws Exception {
        ExportMetricsServiceRequest exportRequest = ExportMetricsServiceRequest.newBuilder().build();
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(exportRequest.toByteArray())
                .address("http://collector.example/custom")
                .headers(Map.of("api-key", "secret"))
                .compressionMode(CompressionMode.GZIP)
                .build();

        assertThat(request.getAddress()).isEqualTo("http://collector.example/custom");
        assertThat(request.getHeaders()).containsEntry("api-key", "secret");
        assertThat(request.getCompressionMode()).isEqualTo(CompressionMode.GZIP);
        assertThat(ExportMetricsServiceRequest.parseFrom(request.getMetricsData()))
                .isEqualTo(exportRequest);
        assertThat(request.toString())
                .contains("http://collector.example/custom")
                .contains("GZIP")
                .contains("api-key");
    }

    private static OtlpMeterRegistry registry(TestOtlpConfig config, CapturingMetricsSender sender) {
        return OtlpMeterRegistry.builder(config)
                .clock(new FixedWallClock())
                .metricsSender(sender)
                .threadFactory(runnable -> {
                    Thread thread = new Thread(runnable, "otlp-test-publisher");
                    thread.setDaemon(true);
                    return thread;
                })
                .build();
    }

    private static ExportMetricsServiceRequest exportRequest(OtlpMetricsSender.Request request) throws Exception {
        return ExportMetricsServiceRequest.parseFrom(request.getMetricsData());
    }

    private static Map<String, String> resourceAttributes(ExportMetricsServiceRequest request) {
        Map<String, String> attributes = new HashMap<>();
        for (KeyValue keyValue : request.getResourceMetrics(0).getResource().getAttributesList()) {
            attributes.put(keyValue.getKey(), keyValue.getValue().getStringValue());
        }
        return attributes;
    }

    private static Map<String, Metric> metricsByName(ExportMetricsServiceRequest request) {
        Map<String, Metric> metrics = new HashMap<>();
        request.getResourceMetricsList().forEach(resourceMetrics -> resourceMetrics.getScopeMetricsList()
                .forEach(scopeMetrics -> scopeMetrics.getMetricsList()
                        .forEach(metric -> metrics.put(metric.getName(), metric))));
        return metrics;
    }

    private static Map<String, String> attributes(Metric metric) {
        List<KeyValue> attributes = metric.hasGauge() ? metric.getGauge().getDataPoints(0).getAttributesList()
                : metric.hasSum() ? metric.getSum().getDataPoints(0).getAttributesList()
                : metric.hasHistogram() ? metric.getHistogram().getDataPoints(0).getAttributesList()
                : metric.getExponentialHistogram().getDataPoints(0).getAttributesList();
        Map<String, String> mappedAttributes = new HashMap<>();
        for (KeyValue attribute : attributes) {
            mappedAttributes.put(attribute.getKey(), attribute.getValue().getStringValue());
        }
        return mappedAttributes;
    }

    private static class FixedWallClock implements Clock {
        @Override
        public long wallTime() {
            return 1L;
        }

        @Override
        public long monotonicTime() {
            return System.nanoTime();
        }
    }

    private static class CapturingMetricsSender implements OtlpMetricsSender {
        private final List<OtlpMetricsSender.Request> requests = new ArrayList<>();

        @Override
        public void send(OtlpMetricsSender.Request request) {
            requests.add(request);
        }

        OtlpMetricsSender.Request singleRequest() {
            assertThat(requests).hasSize(1);
            return requests.get(0);
        }
    }

    private static class TestOtlpConfig implements OtlpConfig {
        private final String url;

        private final Map<String, String> resourceAttributes;

        private final Map<String, String> headers;

        TestOtlpConfig(String url, Map<String, String> resourceAttributes, Map<String, String> headers) {
            this.url = url;
            this.resourceAttributes = resourceAttributes;
            this.headers = headers;
        }

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String url() {
            return url;
        }

        @Override
        public Duration step() {
            return Duration.ofSeconds(10);
        }

        @Override
        public Map<String, String> resourceAttributes() {
            return resourceAttributes;
        }

        @Override
        public Map<String, String> headers() {
            return headers;
        }
    }
}
