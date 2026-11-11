/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.config.EscapingScheme;
import io.prometheus.metrics.expositionformats.PrometheusProtobufWriter;
import io.prometheus.metrics.expositionformats.generated.com_google_protobuf_4_33_0.Metrics;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.CounterSnapshot.CounterDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricMetadata;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.TextFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PrometheusProtobufWriterImplTest {
    @Test
    void writesDelimitedCounterWithLabelsAndTimestamps() throws Exception {
        PrometheusProtobufWriter writer = new PrometheusProtobufWriter();
        CounterDataPointSnapshot point = CounterDataPointSnapshot.builder()
                .value(42.5)
                .labels(Labels.of("method", "GET"))
                .createdTimestampMillis(12000)
                .scrapeTimestampMillis(15000)
                .build();
        MetricSnapshots snapshots = MetricSnapshots.of(new CounterSnapshot(
                new MetricMetadata("requests", "Handled requests"), List.of(point)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writer.write(output, snapshots, EscapingScheme.UNDERSCORE_ESCAPING);

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        Metrics.MetricFamily family = Metrics.MetricFamily.parseDelimitedFrom(input);
        assertThat(family.getName()).isEqualTo("requests_total");
        assertThat(family.getHelp()).isEqualTo("Handled requests");
        assertThat(family.getType()).isEqualTo(Metrics.MetricType.COUNTER);
        assertThat(family.getMetricCount()).isEqualTo(1);
        Metrics.Metric metric = family.getMetric(0);
        assertThat(metric.getCounter().getValue()).isEqualTo(42.5);
        assertThat(metric.getCounter().getCreatedTimestamp().getSeconds()).isEqualTo(12);
        assertThat(metric.getTimestampMs()).isEqualTo(15000);
        assertThat(metric.getLabel(0).getName()).isEqualTo("method");
        assertThat(metric.getLabel(0).getValue()).isEqualTo("GET");
        assertThat(Metrics.MetricFamily.parseDelimitedFrom(input)).isNull();
    }

    @Test
    void rendersDebugTextAndOmitsEmptySnapshots() throws Exception {
        PrometheusProtobufWriter writer = new PrometheusProtobufWriter();
        MetricSnapshots snapshots = MetricSnapshots.of(
                new CounterSnapshot(new MetricMetadata("empty"), List.of()),
                new CounterSnapshot(new MetricMetadata("jobs"), List.of(
                        CounterDataPointSnapshot.builder().value(7).build())));

        String text = writer.toDebugString(snapshots, EscapingScheme.UNDERSCORE_ESCAPING);
        Metrics.MetricFamily family = TextFormat.parse(text, Metrics.MetricFamily.class);

        assertThat(family.getName()).isEqualTo("jobs_total");
        assertThat(family.getMetric(0).getCounter().getValue()).isEqualTo(7);
        assertThat(text).doesNotContain("empty");
    }
}
