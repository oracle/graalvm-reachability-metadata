/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.expositionformats.generated.com_google_protobuf_4_33_0.Metrics;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageLiteInnerSerializedFormTest {
    @Test
    void roundTripsSerializableMetricFamily() throws Exception {
        Metrics.MetricFamily original = Metrics.MetricFamily.newBuilder()
                .setName("queue_size")
                .setHelp("Waiting jobs")
                .setType(Metrics.MetricType.GAUGE)
                .addMetric(Metrics.Metric.newBuilder()
                        .setGauge(Metrics.Gauge.newBuilder().setValue(12.5))
                        .addLabel(Metrics.LabelPair.newBuilder().setName("queue").setValue("urgent")))
                .addMetric(Metrics.Metric.newBuilder()
                        .setGauge(Metrics.Gauge.newBuilder().setValue(3))
                        .addLabel(Metrics.LabelPair.newBuilder().setName("queue").setValue("batch")))
                .build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        Metrics.MetricFamily restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Metrics.MetricFamily) input.readObject();
        }

        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        assertThat(restored.getMetricCount()).isEqualTo(2);
        assertThat(restored.getMetric(0).getGauge().getValue()).isEqualTo(12.5);
        assertThat(restored.getMetric(1).getLabel(0).getValue()).isEqualTo("batch");
        assertThat(restored.toByteArray()).isEqualTo(original.toByteArray());
    }
}
