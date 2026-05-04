/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.simpleclient;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.CounterMetricFamily;
import io.prometheus.client.Enumeration;
import io.prometheus.client.Gauge;
import io.prometheus.client.GaugeMetricFamily;
import io.prometheus.client.Histogram;
import io.prometheus.client.Info;
import io.prometheus.client.SampleNameFilter;
import io.prometheus.client.Summary;
import io.prometheus.client.SummaryMetricFamily;
import io.prometheus.client.exemplars.Exemplar;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.offset;

public class SimpleclientTest {
    @Test
    void counterRegistersLabeledSamplesAndRejectsNegativeIncrements() {
        CollectorRegistry registry = new CollectorRegistry();
        Counter counter = Counter.build()
                .name("jobs_processed_total")
                .help("Processed jobs.")
                .labelNames("queue", "outcome")
                .register(registry);

        counter.labels("fast", "success").inc();
        counter.labels("fast", "success").inc(2.5);
        counter.labels("fast", "failure").inc();

        assertThat(counter.labels("fast", "success").get()).isEqualTo(3.5);
        assertThat(registry.getSampleValue(
                "jobs_processed_total",
                new String[] {"queue", "outcome"},
                new String[] {"fast", "success"})).isEqualTo(3.5);
        assertThat(registry.getSampleValue(
                "jobs_processed_total",
                new String[] {"queue", "outcome"},
                new String[] {"fast", "failure"})).isEqualTo(1.0);

        MetricFamilySamples family = singleFamily(counter.collect());
        assertThat(family.type).isEqualTo(Collector.Type.COUNTER);
        assertThat(family.name).isEqualTo("jobs_processed");
        assertThat(family.samples)
                .extracting(sample -> sample.name)
                .contains("jobs_processed_total");

        List<MetricFamilySamples> filtered = Collections.list(
                registry.filteredMetricFamilySamples(Set.of("jobs_processed_total")));
        assertThat(filtered).singleElement().extracting(metricFamily -> metricFamily.name).isEqualTo("jobs_processed");

        assertThatIllegalArgumentException().isThrownBy(() -> counter.labels("fast", "success").inc(-1.0));
    }

    @Test
    void counterSamplesCanCarryExplicitExemplars() {
        Counter counter = Counter.build()
                .name("requests_total")
                .help("Requests.")
                .withExemplarSampler((amount, previous) -> previous)
                .create();

        counter.incWithExemplar(4.0, "trace_id", "abc123", "span_id", "def456");

        Sample total = findSample(singleFamily(counter.collect()), "requests_total");
        assertThat(total.value).isEqualTo(4.0);
        assertThat(total.exemplar).isNotNull();
        assertThat(total.exemplar.getValue()).isEqualTo(4.0);
        assertThat(total.exemplar.getNumberOfLabels()).isEqualTo(2);
        assertThat(total.exemplar.getLabelName(0)).isEqualTo("span_id");
        assertThat(total.exemplar.getLabelValue(0)).isEqualTo("def456");
        assertThat(total.exemplar.getLabelName(1)).isEqualTo("trace_id");
        assertThat(total.exemplar.getLabelValue(1)).isEqualTo("abc123");
    }

    @Test
    void gaugeSupportsChildLifecycleAndTimingHelpers() throws Exception {
        CollectorRegistry registry = new CollectorRegistry();
        Gauge gauge = Gauge.build("queue_depth", "Current queue depth.")
                .labelNames("queue")
                .register(registry);

        Gauge.Child highPriority = gauge.labels("high");
        highPriority.inc(5.0);
        highPriority.dec();
        highPriority.dec(0.5);
        highPriority.set(7.25);

        assertThat(highPriority.get()).isEqualTo(7.25);
        assertThat(registry.getSampleValue("queue_depth", new String[] {"queue"}, new String[] {"high"}))
                .isEqualTo(7.25);

        Callable<String> measuredWork = () -> "finished";
        assertThat(highPriority.setToTime(measuredWork)).isEqualTo("finished");
        assertThat(highPriority.get()).isGreaterThanOrEqualTo(0.0);

        try (Gauge.Timer timer = highPriority.startTimer()) {
            assertThat(timer).isNotNull();
        }
        assertThat(highPriority.get()).isGreaterThanOrEqualTo(0.0);

        gauge.remove("high");
        assertThat(registry.getSampleValue("queue_depth", new String[] {"queue"}, new String[] {"high"})).isNull();
    }

    @Test
    void histogramPublishesCumulativeBucketsCountAndSum() {
        CollectorRegistry registry = new CollectorRegistry();
        Histogram histogram = Histogram.build()
                .name("request_duration_seconds")
                .help("Request duration.")
                .labelNames("endpoint")
                .buckets(0.1, 0.5, 1.0)
                .register(registry);

        histogram.labels("/api").observe(0.05);
        histogram.labels("/api").observe(0.7);

        assertThat(registry.getSampleValue(
                "request_duration_seconds_bucket",
                new String[] {"endpoint", "le"},
                new String[] {"/api", "0.1"})).isEqualTo(1.0);
        assertThat(registry.getSampleValue(
                "request_duration_seconds_bucket",
                new String[] {"endpoint", "le"},
                new String[] {"/api", "0.5"})).isEqualTo(1.0);
        assertThat(registry.getSampleValue(
                "request_duration_seconds_bucket",
                new String[] {"endpoint", "le"},
                new String[] {"/api", "1.0"})).isEqualTo(2.0);
        assertThat(registry.getSampleValue(
                "request_duration_seconds_bucket",
                new String[] {"endpoint", "le"},
                new String[] {"/api", "+Inf"})).isEqualTo(2.0);
        assertThat(registry.getSampleValue(
                "request_duration_seconds_count",
                new String[] {"endpoint"},
                new String[] {"/api"})).isEqualTo(2.0);
        assertThat(registry.getSampleValue(
                "request_duration_seconds_sum",
                new String[] {"endpoint"},
                new String[] {"/api"})).isCloseTo(0.75, offset(0.000_000_1));

        Histogram.Child.Value value = histogram.labels("/api").get();
        assertThat(value.buckets[value.buckets.length - 1]).isEqualTo(2.0);
        assertThat(value.sum).isCloseTo(0.75, offset(0.000_000_1));
    }

    @Test
    void histogramSamplesCanCarryExplicitExemplarsOnBuckets() {
        Histogram histogram = Histogram.build("rpc_latency_seconds", "RPC latency.")
                .labelNames("method")
                .buckets(0.1, 0.5, 1.0)
                .create();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("trace_id", "trace-456");
        labels.put("span_id", "span-789");

        Histogram.Child child = histogram.labels("GET");
        child.observeWithExemplar(0.2, labels);

        Histogram.Child.Value value = child.get();
        assertThat(value.buckets).containsExactly(0.0, 1.0, 1.0, 1.0);
        assertThat(value.exemplars[0]).isNull();
        assertThat(value.exemplars[1]).isNotNull();
        assertThat(value.exemplars[1].getValue()).isEqualTo(0.2);
        assertThat(value.exemplars[1].getNumberOfLabels()).isEqualTo(2);
        assertThat(value.exemplars[1].getLabelName(0)).isEqualTo("span_id");
        assertThat(value.exemplars[1].getLabelValue(0)).isEqualTo("span-789");
        assertThat(value.exemplars[1].getLabelName(1)).isEqualTo("trace_id");
        assertThat(value.exemplars[1].getLabelValue(1)).isEqualTo("trace-456");

        Sample bucket = findSample(
                singleFamily(histogram.collect()),
                "rpc_latency_seconds_bucket",
                List.of("method", "le"),
                List.of("GET", "0.5"));
        assertThat(bucket.value).isEqualTo(1.0);
        assertThat(bucket.exemplar).isNotNull();
        assertThat(bucket.exemplar.getValue()).isEqualTo(0.2);
    }

    @Test
    void histogramTimingHelpersRecordCallableAndRunnableDurations() {
        Histogram histogram = Histogram.build("operation_seconds", "Operation duration.")
                .buckets(0.001, 0.01, 1.0)
                .create();

        assertThat(histogram.time(() -> "ok")).isEqualTo("ok");
        double runnableDuration = histogram.time(() -> {
        });
        try (Histogram.Timer timer = histogram.startTimer()) {
            assertThat(timer).isNotNull();
        }

        assertThat(runnableDuration).isGreaterThanOrEqualTo(0.0);
        assertThat(findSample(singleFamily(histogram.collect()), "operation_seconds_count").value).isEqualTo(3.0);
    }

    @Test
    void summaryTracksCountSumAndConfiguredQuantiles() {
        CollectorRegistry registry = new CollectorRegistry();
        Summary summary = Summary.build()
                .name("payload_size_bytes")
                .help("Payload size.")
                .labelNames("route")
                .quantile(0.5, 0.05)
                .quantile(0.9, 0.01)
                .register(registry);

        Summary.Child child = summary.labels("upload");
        child.observe(10.0);
        child.observe(20.0);
        child.observe(30.0);

        Summary.Child.Value value = child.get();
        assertThat(value.count).isEqualTo(3.0);
        assertThat(value.sum).isEqualTo(60.0);
        assertThat(value.quantiles.keySet()).containsExactly(0.5, 0.9);
        assertThat(value.created).isPositive();
        assertThat(registry.getSampleValue(
                "payload_size_bytes_count",
                new String[] {"route"},
                new String[] {"upload"})).isEqualTo(3.0);
        assertThat(registry.getSampleValue(
                "payload_size_bytes_sum",
                new String[] {"route"},
                new String[] {"upload"})).isEqualTo(60.0);

        assertThat(child.time(() -> "timed-result")).isEqualTo("timed-result");
        assertThat(child.get().count).isEqualTo(4.0);
    }

    @Test
    void infoAndEnumerationExposeMetadataAsMetricSamples() {
        InfoAndEnumerationMetrics metrics = new InfoAndEnumerationMetrics();

        assertThat(metrics.info.get())
                .containsEntry("version", "test-version")
                .containsEntry("revision", "abc123")
                .hasSize(2);
        assertThat(metrics.state.get()).isEqualTo("running");

        MetricFamilySamples infoFamily = singleFamily(metrics.info.collect());
        Sample infoSample = findSample(infoFamily, "application_info");
        assertThat(infoSample.value).isEqualTo(1.0);
        assertThat(infoSample.labelNames).containsExactly("revision", "version");
        assertThat(infoSample.labelValues).containsExactly("abc123", "test-version");

        MetricFamilySamples stateFamily = singleFamily(metrics.state.collect());
        assertThat(stateFamily.type).isEqualTo(Collector.Type.STATE_SET);
        assertThat(stateFamily.samples).hasSize(3);
        assertThat(samplesWithValue(stateFamily, 1.0))
                .singleElement()
                .satisfies(sample -> assertThat(sample.labelValues).contains("running"));
    }

    @Test
    void collectorRegistryManagesCollectorLifecycle() {
        CollectorRegistry registry = new CollectorRegistry();
        Counter processed = Counter.build("lifecycle_events_total", "Lifecycle events.")
                .register(registry);
        processed.inc(2.0);

        assertThat(registry.getSampleValue("lifecycle_events_total")).isEqualTo(2.0);

        Counter duplicate = Counter.build("lifecycle_events_total", "Duplicate lifecycle events.")
                .create();
        assertThatIllegalArgumentException().isThrownBy(() -> duplicate.register(registry));

        registry.unregister(processed);
        assertThat(registry.getSampleValue("lifecycle_events_total")).isNull();
        assertThat(Collections.list(registry.metricFamilySamples())).isEmpty();

        processed.register(registry);
        assertThat(registry.getSampleValue("lifecycle_events_total")).isEqualTo(2.0);

        Gauge activeSessions = Gauge.build("active_sessions", "Active sessions.")
                .register(registry);
        activeSessions.set(3.0);
        assertThat(registry.getSampleValue("active_sessions")).isEqualTo(3.0);

        registry.clear();
        assertThat(registry.getSampleValue("lifecycle_events_total")).isNull();
        assertThat(registry.getSampleValue("active_sessions")).isNull();
        assertThat(Collections.list(registry.metricFamilySamples())).isEmpty();
    }

    @Test
    void customMetricFamiliesCanBeCollectedAndFiltered() {
        CollectorRegistry registry = new CollectorRegistry();
        Collector customCollector = new Collector() {
            @Override
            public List<MetricFamilySamples> collect() {
                CounterMetricFamily jobs = new CounterMetricFamily("custom_jobs", "Custom jobs.", List.of("worker"));
                jobs.addMetric(List.of("alpha"), 2.0);

                GaugeMetricFamily load = new GaugeMetricFamily("custom_load", "Custom load.", List.of("worker"));
                load.addMetric(List.of("alpha"), 0.75);

                SummaryMetricFamily sizes = new SummaryMetricFamily(
                        "custom_size_bytes", "Custom size.", List.of("worker"));
                sizes.addMetric(List.of("alpha"), 3.0, 21.0);

                return List.of(jobs, load, sizes);
            }
        }.register(registry);

        assertThat(registry.getSampleValue("custom_jobs_total", new String[] {"worker"}, new String[] {"alpha"}))
                .isEqualTo(2.0);
        assertThat(registry.getSampleValue("custom_load", new String[] {"worker"}, new String[] {"alpha"}))
                .isEqualTo(0.75);
        assertThat(registry.getSampleValue("custom_size_bytes_count", new String[] {"worker"}, new String[] {"alpha"}))
                .isEqualTo(3.0);
        assertThat(registry.getSampleValue("custom_size_bytes_sum", new String[] {"worker"}, new String[] {"alpha"}))
                .isEqualTo(21.0);

        List<MetricFamilySamples> onlyLoad = customCollector.collect(
                SampleNameFilter.restrictToNamesEqualTo(SampleNameFilter.ALLOW_ALL, Set.of("custom_load")));
        assertThat(onlyLoad).singleElement().extracting(metricFamily -> metricFamily.name).isEqualTo("custom_load");
    }

    @Test
    void collectorUtilitiesAndExemplarValueObjectsUsePrometheusConventions() {
        assertThat(Collector.sanitizeMetricName("http.server-requests$total")).isEqualTo("http_server_requests_total");
        assertThat(Collector.doubleToGoString(Double.POSITIVE_INFINITY)).isEqualTo("+Inf");
        assertThat(Collector.doubleToGoString(Double.NEGATIVE_INFINITY)).isEqualTo("-Inf");
        assertThat(Collector.doubleToGoString(Double.NaN)).isEqualTo("NaN");
        assertThat(SampleNameFilter.stringToList("alpha,beta,,gamma")).containsExactly("alpha", "beta", "gamma");

        Map<String, String> exemplarLabels = new LinkedHashMap<>();
        exemplarLabels.put("trace_id", "trace-1");
        exemplarLabels.put("span_id", "span-1");
        Exemplar exemplar = new Exemplar(12.5, 1234L, exemplarLabels);

        assertThat(exemplar.getValue()).isEqualTo(12.5);
        assertThat(exemplar.getTimestampMs()).isEqualTo(1234L);
        assertThat(exemplar.getNumberOfLabels()).isEqualTo(2);
        assertThat(exemplar.getLabelName(0)).isEqualTo("span_id");
        assertThat(exemplar.getLabelValue(0)).isEqualTo("span-1");
        assertThat(Exemplar.mapToArray(exemplarLabels))
                .containsExactly("trace_id", "trace-1", "span_id", "span-1");
    }

    private static MetricFamilySamples singleFamily(List<MetricFamilySamples> samples) {
        assertThat(samples).hasSize(1);
        return samples.get(0);
    }

    private static Sample findSample(MetricFamilySamples family, String sampleName) {
        return family.samples.stream()
                .filter(sample -> sample.name.equals(sampleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing sample " + sampleName + " in " + family.name));
    }

    private static Sample findSample(
            MetricFamilySamples family,
            String sampleName,
            List<String> labelNames,
            List<String> labelValues) {
        return family.samples.stream()
                .filter(sample -> sample.name.equals(sampleName))
                .filter(sample -> sample.labelNames.equals(labelNames))
                .filter(sample -> sample.labelValues.equals(labelValues))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing sample " + sampleName + " in " + family.name));
    }

    private static List<Sample> samplesWithValue(MetricFamilySamples family, double value) {
        List<Sample> matches = new ArrayList<>();
        for (Sample sample : family.samples) {
            if (sample.value == value) {
                matches.add(sample);
            }
        }
        return matches;
    }

    private static final class InfoAndEnumerationMetrics {
        private final Info info;
        private final Enumeration state;

        private InfoAndEnumerationMetrics() {
            info = Info.build("application", "Application metadata.")
                    .create();
            info.info("version", "test-version", "revision", "abc123");

            state = Enumeration.build("component_state", "Component state.")
                    .states("starting", "running", "stopped")
                    .create();
            state.state("running");
        }
    }
}
