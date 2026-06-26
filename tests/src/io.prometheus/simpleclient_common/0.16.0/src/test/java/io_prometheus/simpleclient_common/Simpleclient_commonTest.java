/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.simpleclient_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Simpleclient_commonTest {
    @Test
    void chooseContentTypeDefaultsTo004AndRecognizesOpenMetricsAcceptHeader() {
        assertThat(TextFormat.chooseContentType(null)).isEqualTo(TextFormat.CONTENT_TYPE_004);
        assertThat(TextFormat.chooseContentType("text/plain")).isEqualTo(TextFormat.CONTENT_TYPE_004);
        assertThat(TextFormat.chooseContentType("text/plain;q=0.4, application/openmetrics-text; version=1.0.0"))
                .isEqualTo(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
    }

    @Test
    void write004ExportsRegistryMetricsWithEscaping() throws IOException {
        CollectorRegistry registry = new CollectorRegistry();
        Counter counter = Counter.build("requests", "Requests by route")
                .namespace("sample")
                .labelNames("method")
                .register(registry);
        counter.labels("GET\\root\n\"quoted\"").inc(2.0);

        Gauge gauge = Gauge.build("queue_depth", "Depth with \\ slash and\nnewline")
                .namespace("sample")
                .labelNames("queue")
                .register(registry);
        gauge.labels("critical").set(Double.POSITIVE_INFINITY);

        String output = write004(registry.metricFamilySamples());

        assertThat(output)
                .contains("# HELP sample_requests_total Requests by route")
                .contains("# TYPE sample_requests_total counter")
                .contains("sample_requests_total{method=\"GET\\\\root\\n\\\"quoted\\\"\"")
                .contains(" 2.0")
                .contains("# HELP sample_queue_depth Depth with \\\\ slash and\\nnewline")
                .contains("# TYPE sample_queue_depth gauge")
                .contains("sample_queue_depth{queue=\"critical\"")
                .contains(" +Inf");
    }

    @Test
    void write004SplitsCreatedSamplesIntoGaugeFamilies() throws IOException {
        MetricFamilySamples counter = metricFamily(
                "sample_requests",
                Type.COUNTER,
                "Requests by route",
                sample("sample_requests_total", List.of("method"), List.of("GET"), 2.0),
                sample("sample_requests_created", List.of("method"), List.of("GET"), 123.456));

        String output = write004(counter);

        assertThat(output)
                .contains("# HELP sample_requests_total Requests by route")
                .contains("# TYPE sample_requests_total counter")
                .contains("sample_requests_total{method=\"GET\"")
                .contains("# HELP sample_requests_created Requests by route")
                .contains("# TYPE sample_requests_created gauge")
                .contains("sample_requests_created{method=\"GET\"")
                .contains(" 123.456");
    }

    @Test
    void write004RendersInfoSamplesWithTheTextFormatInfoSuffix() throws IOException {
        MetricFamilySamples info = metricFamily(
                "build",
                Type.INFO,
                "Build metadata",
                sample("build_info", List.of("version", "commit"), List.of("release", "abc123"), 1.0));

        String output = write004(info);

        assertThat(output)
                .contains("# HELP build_info Build metadata")
                .contains("# TYPE build_info gauge")
                .contains("build_info{version=\"release\",commit=\"abc123\"")
                .contains(" 1.0");
    }

    @Test
    void writeOpenMetrics100ExportsUnitsTimestampsExemplarsAndEof() throws IOException {
        Exemplar exemplar = new Exemplar(7.0, 5_678L, "trace_id", "abc", "span", "s\\\"1");
        MetricFamilySamples metrics = new MetricFamilySamples(
                "payload_bytes",
                "bytes",
                Type.GAUGE,
                "Payload help with \\ slash, \"quote\" and\nnewline",
                List.of(new Sample(
                        "payload_bytes",
                        List.of("path", "status"),
                        List.of("/api\\v1\n\"quoted\"", "200"),
                        3.5,
                        exemplar,
                        1_234L)));

        String output = writeOpenMetrics(metrics);

        assertThat(output)
                .contains("# TYPE payload_bytes gauge")
                .contains("# UNIT payload_bytes bytes")
                .contains("# HELP payload_bytes Payload help with \\\\ slash, \\\"quote\\\" and\\nnewline")
                .contains("payload_bytes{path=\"/api\\\\v1\\n\\\"quoted\\\"\",status=\"200\"} 3.5 1.234")
                .contains(" # {span=\"s\\\\\\\"1\",trace_id=\"abc\"} 7.0 5.678")
                .endsWith("# EOF\n");
    }

    @Test
    void writeOpenMetrics100PreservesOpenMetricsSpecificTypes() throws IOException {
        MetricFamilySamples info = metricFamily(
                "build",
                Type.INFO,
                "Build metadata",
                sample("build_info", List.of("version"), List.of("release"), 1.0));
        MetricFamilySamples stateSet = metricFamily(
                "feature_enabled",
                Type.STATE_SET,
                "Feature flags",
                sample("feature_enabled", List.of("feature", "feature_enabled"), List.of("dark_launch", "true"), 1.0));
        MetricFamilySamples gaugeHistogram = metricFamily(
                "temperatures",
                Type.GAUGE_HISTOGRAM,
                "Temperature distribution",
                sample("temperatures_gcount", List.of(), List.of(), 2.0));

        String output = writeOpenMetrics(info, stateSet, gaugeHistogram);

        assertThat(output)
                .contains("# TYPE build info")
                .contains("build_info{version=\"release\"} 1.0")
                .contains("# TYPE feature_enabled stateset")
                .contains("feature_enabled{feature=\"dark_launch\",feature_enabled=\"true\"} 1.0")
                .contains("# TYPE temperatures gaugehistogram")
                .contains("temperatures_gcount 2.0")
                .endsWith("# EOF\n");
    }

    @Test
    void writeFormatDispatchesToKnownFormatsAndRejectsUnknownContentTypes() throws IOException {
        MetricFamilySamples metric = metricFamily(
                "temperature_celsius",
                Type.GAUGE,
                "Temperature",
                sample("temperature_celsius", List.of("room"), List.of("server"), 21.5));

        String format004 = writeFormat(TextFormat.CONTENT_TYPE_004, metric);
        String direct004 = write004(metric);
        String openMetrics = writeFormat(TextFormat.CONTENT_TYPE_OPENMETRICS_100, metric);

        assertThat(format004).isEqualTo(direct004);
        assertThat(openMetrics)
                .contains("# TYPE temperature_celsius gauge")
                .contains("temperature_celsius{room=\"server\"} 21.5")
                .endsWith("# EOF\n");
        assertThatThrownBy(() -> writeFormat("application/json", metric))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown contentType application/json");
    }

    private static String writeFormat(String contentType, MetricFamilySamples... metricFamilies) throws IOException {
        StringWriter writer = new StringWriter();
        TextFormat.writeFormat(contentType, writer, enumeration(metricFamilies));
        return writer.toString();
    }

    private static String write004(MetricFamilySamples... metricFamilies) throws IOException {
        return write004(enumeration(metricFamilies));
    }

    private static String write004(Enumeration<MetricFamilySamples> metricFamilies) throws IOException {
        StringWriter writer = new StringWriter();
        TextFormat.write004(writer, metricFamilies);
        return writer.toString();
    }

    private static String writeOpenMetrics(MetricFamilySamples... metricFamilies) throws IOException {
        StringWriter writer = new StringWriter();
        TextFormat.writeOpenMetrics100(writer, enumeration(metricFamilies));
        return writer.toString();
    }

    private static MetricFamilySamples metricFamily(
            String name,
            Type type,
            String help,
            Sample... samples) {
        return new MetricFamilySamples(name, type, help, List.of(samples));
    }

    private static Sample sample(
            String name,
            List<String> labelNames,
            List<String> labelValues,
            double value) {
        return new Sample(name, labelNames, labelValues, value);
    }

    private static Enumeration<MetricFamilySamples> enumeration(MetricFamilySamples... metricFamilies) {
        return Collections.enumeration(List.of(metricFamilies));
    }
}
