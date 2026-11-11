/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.expositionformats.generated.com_google_protobuf_4_33_0.Metrics;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.TextFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageTest {
    @Test
    void printsAndMergesNestedMetricText() throws Exception {
        Metrics.MetricFamily original = Metrics.MetricFamily.newBuilder()
                .setName("queue_depth")
                .setHelp("Queued work")
                .setType(Metrics.MetricType.GAUGE)
                .addMetric(Metrics.Metric.newBuilder()
                        .setGauge(Metrics.Gauge.newBuilder().setValue(3.5))
                        .addLabel(Metrics.LabelPair.newBuilder().setName("queue").setValue("worker")))
                .build();

        String text = TextFormat.printer().printToString(original);
        Metrics.MetricFamily.Builder builder = Metrics.MetricFamily.newBuilder();
        TextFormat.merge(text, builder);
        Metrics.MetricFamily decoded = builder.build();

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.getMetric(0).getGauge().getValue()).isEqualTo(3.5);
        assertThat(text).contains("queue_depth", "Queued work", "worker");
    }
}
