/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.simpleclient_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Info;
import io.prometheus.client.Summary;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TextFormatTest {
    @Test
    void choosesOpenMetricsOnlyWhenAcceptedExplicitly() {
        assertThat(TextFormat.chooseContentType(null)).isEqualTo(TextFormat.CONTENT_TYPE_004);
        assertThat(TextFormat.chooseContentType("")).isEqualTo(TextFormat.CONTENT_TYPE_004);
        assertThat(TextFormat.chooseContentType("text/plain; version=0.0.4"))
                .isEqualTo(TextFormat.CONTENT_TYPE_004);
        assertThat(TextFormat.chooseContentType("application/json, */*;q=0.1"))
                .isEqualTo(TextFormat.CONTENT_TYPE_004);
        assertThat(TextFormat.chooseContentType(
                "application/openmetrics-text; version=1.0.0, text/plain; version=0.0.4;q=0.5"))
                .isEqualTo(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
    }

    @Test
    void writeFormatDispatchesByContentTypeAndRejectsUnknownContentTypes() throws IOException {
        MetricFamilySamples family = new MetricFamilySamples(
                "temperature_celsius",
                "celsius",
                Collector.Type.GAUGE,
                "Room temperature",
                List.of(new Sample("temperature_celsius", List.of("room"), List.of("lab"), 21.5, 1_678_000_123L)));

        StringWriter textWriter = new StringWriter();
        TextFormat.writeFormat(TextFormat.CONTENT_TYPE_004, textWriter, enumerationOf(family));
        assertThat(textWriter.toString()).isEqualTo("""
                # HELP temperature_celsius Room temperature
                # TYPE temperature_celsius gauge
                temperature_celsius{room=\"lab\",} 21.5 1678000123
                """);

        StringWriter openMetricsWriter = new StringWriter();
        TextFormat.writeFormat(TextFormat.CONTENT_TYPE_OPENMETRICS_100, openMetricsWriter, enumerationOf(family));
        assertThat(openMetricsWriter.toString()).isEqualTo("""
                # TYPE temperature_celsius gauge
                # UNIT temperature_celsius celsius
                # HELP temperature_celsius Room temperature
                temperature_celsius{room=\"lab\"} 21.5 1678000.123
                # EOF
                """);

        assertThatThrownBy(() -> TextFormat.writeFormat("application/json", new StringWriter(), enumerationOf(family)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown contentType application/json");
    }

    @Test
    void writesPrometheusTextFormatForCoreCollectorTypes() throws IOException {
        CollectorRegistry registry = new CollectorRegistry();
        Gauge.build()
                .name("queue_depth")
                .help("Queued work")
                .labelNames("queue")
                .register(registry)
                .labels("critical")
                .set(Double.POSITIVE_INFINITY);
        Counter.build()
                .name("requests")
                .help("Handled requests")
                .register(registry)
                .inc(3.0);
        Summary.build()
                .name("request_latency_seconds")
                .help("Request latency")
                .quantile(0.5, 0.01)
                .register(registry)
                .observe(0.25);
        Histogram.build()
                .name("payload_bytes")
                .help("Payload size")
                .buckets(100.0, 1_000.0)
                .register(registry)
                .observe(42.0);
        Info.build()
                .name("build")
                .help("Build metadata")
                .register(registry)
                .info("version", "test");

        String output = write004(registry.metricFamilySamples())
                .replaceAll("_created(\\{[^}]*})? [0-9E.]+", "_created$1 1234.0");

        assertThat(output).contains("""
                # HELP queue_depth Queued work
                # TYPE queue_depth gauge
                queue_depth{queue=\"critical\",} +Inf
                """);
        assertThat(output).contains("""
                # HELP requests_total Handled requests
                # TYPE requests_total counter
                requests_total 3.0
                """);
        assertThat(output).contains("""
                # HELP requests_created Handled requests
                # TYPE requests_created gauge
                requests_created 1234.0
                """);
        assertThat(output).contains("""
                # HELP request_latency_seconds Request latency
                # TYPE request_latency_seconds summary
                request_latency_seconds{quantile=\"0.5\",} 0.25
                request_latency_seconds_count 1.0
                request_latency_seconds_sum 0.25
                """);
        assertThat(output).contains("""
                # HELP payload_bytes Payload size
                # TYPE payload_bytes histogram
                payload_bytes_bucket{le=\"100.0\",} 1.0
                payload_bytes_bucket{le=\"1000.0\",} 1.0
                payload_bytes_bucket{le=\"+Inf\",} 1.0
                payload_bytes_count 1.0
                payload_bytes_sum 42.0
                """);
        assertThat(output).contains("""
                # HELP build_info Build metadata
                # TYPE build_info gauge
                build_info{version=\"test\",} 1.0
                """);
    }

    @Test
    void writesPrometheusTextFormatWithEscapingTimestampsAndOpenMetricsSpecificSamples() throws IOException {
        MetricFamilySamples family = new MetricFamilySamples(
                "processing_seconds",
                Collector.Type.GAUGE_HISTOGRAM,
                "Line one\npath \\server \"quoted\"",
                List.of(
                        new Sample(
                                "processing_seconds_bucket",
                                List.of("le", "worker"),
                                List.of("+Inf", "a\nb\\c\"d"),
                                2.0),
                        new Sample("processing_seconds_gcount", List.of(), List.of(), 2.0),
                        new Sample("processing_seconds_gsum", List.of(), List.of(), 7.0, 1_678_000_123L),
                        new Sample("processing_seconds_created", List.of(), List.of(), 1_234.0)));

        assertThat(write004(enumerationOf(family))).isEqualTo(
                "# HELP processing_seconds Line one\\npath \\\\server \"quoted\"\n"
                        + "# TYPE processing_seconds histogram\n"
                        + "processing_seconds_bucket{le=\"+Inf\",worker=\"a\\nb\\\\c\\\"d\",} 2.0\n"
                        + "# HELP processing_seconds_created Line one\\npath \\\\server \"quoted\"\n"
                        + "# TYPE processing_seconds_created gauge\n"
                        + "processing_seconds_created 1234.0\n"
                        + "# HELP processing_seconds_gcount Line one\\npath \\\\server \"quoted\"\n"
                        + "# TYPE processing_seconds_gcount gauge\n"
                        + "processing_seconds_gcount 2.0\n"
                        + "# HELP processing_seconds_gsum Line one\\npath \\\\server \"quoted\"\n"
                        + "# TYPE processing_seconds_gsum gauge\n"
                        + "processing_seconds_gsum 7.0 1678000123\n");
    }

    @Test
    void writesOpenMetricsFormatWithUnitsExemplarsAndEscaping() throws IOException {
        Exemplar exemplar = new Exemplar(0.75, 1_678_000_456L, "trace_id", "abc123", "span_id", "def456");
        MetricFamilySamples family = new MetricFamilySamples(
                "rpc_duration_seconds",
                "seconds",
                Collector.Type.HISTOGRAM,
                "RPC \"duration\"\nby method",
                List.of(
                        new Sample("rpc_duration_seconds_bucket", List.of("le", "method"), List.of("1.0", "g\net"), 4.0,
                                exemplar, 1_678_000_123L),
                        new Sample("rpc_duration_seconds_count", List.of("method"), List.of("g\net"), 4.0),
                        new Sample("rpc_duration_seconds_sum", List.of("method"), List.of("g\net"), 1.5)));

        assertThat(writeOpenMetrics100(enumerationOf(family))).isEqualTo(
                "# TYPE rpc_duration_seconds histogram\n"
                        + "# UNIT rpc_duration_seconds seconds\n"
                        + "# HELP rpc_duration_seconds RPC \\\"duration\\\"\\nby method\n"
                        + "rpc_duration_seconds_bucket{le=\"1.0\",method=\"g\\net\"} "
                        + "4.0 1678000.123 # {span_id=\"def456\",trace_id=\"abc123\"} 0.75 1678000.456\n"
                        + "rpc_duration_seconds_count{method=\"g\\net\"} 4.0\n"
                        + "rpc_duration_seconds_sum{method=\"g\\net\"} 1.5\n"
                        + "# EOF\n");
    }

    @Test
    void writesOpenMetricsNamesForAllNonCoreTypes() throws IOException {
        MetricFamilySamples unknown = new MetricFamilySamples(
                "raw_metric",
                Collector.Type.UNKNOWN,
                "Raw metric",
                List.of(new Sample("raw_metric", List.of(), List.of(), -1.0)));
        MetricFamilySamples stateSet = new MetricFamilySamples(
                "feature_enabled",
                Collector.Type.STATE_SET,
                "Feature state",
                List.of(new Sample("feature_enabled", List.of("state"), List.of("on"), 1.0)));
        MetricFamilySamples info = new MetricFamilySamples(
                "runtime",
                Collector.Type.INFO,
                "Runtime info",
                List.of(new Sample("runtime_info", List.of("name"), List.of("native"), 1.0)));
        MetricFamilySamples gaugeHistogram = new MetricFamilySamples(
                "temperatures", Collector.Type.GAUGE_HISTOGRAM, "Temperature distribution",
                List.of(new Sample("temperatures_bucket", List.of("le"), List.of("10"), 1.0)));

        assertThat(writeOpenMetrics100(Collections.enumeration(List.of(unknown, stateSet, info, gaugeHistogram))))
                .isEqualTo("""
                        # TYPE raw_metric unknown
                        # HELP raw_metric Raw metric
                        raw_metric -1.0
                        # TYPE feature_enabled stateset
                        # HELP feature_enabled Feature state
                        feature_enabled{state=\"on\"} 1.0
                        # TYPE runtime info
                        # HELP runtime Runtime info
                        runtime_info{name=\"native\"} 1.0
                        # TYPE temperatures gaugehistogram
                        # HELP temperatures Temperature distribution
                        temperatures_bucket{le=\"10\"} 1.0
                        # EOF
                        """);
    }

    private static String write004(Enumeration<MetricFamilySamples> families) throws IOException {
        StringWriter writer = new StringWriter();
        TextFormat.write004(writer, families);
        return writer.toString();
    }

    private static String writeOpenMetrics100(Enumeration<MetricFamilySamples> families) throws IOException {
        StringWriter writer = new StringWriter();
        TextFormat.writeOpenMetrics100(writer, families);
        return writer.toString();
    }

    private static Enumeration<MetricFamilySamples> enumerationOf(MetricFamilySamples family) {
        return Collections.enumeration(List.of(family));
    }
}
