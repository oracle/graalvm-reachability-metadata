/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.expositionformats.generated.com_google_protobuf_4_33_0.Metrics;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeUtilTest {
    @Test
    void parsesMetricFromDirectBufferSlice() throws Exception {
        Metrics.Metric expected = Metrics.Metric.newBuilder()
                .setGauge(Metrics.Gauge.newBuilder().setValue(18.25))
                .addLabel(Metrics.LabelPair.newBuilder().setName("zone").setValue("east"))
                .setTimestampMs(24000)
                .build();
        byte[] bytes = expected.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length + 4);
        buffer.putInt(1234).put(bytes).flip();
        buffer.position(4);

        Metrics.Metric actual = Metrics.Metric.parseFrom(buffer);

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getGauge().getValue()).isEqualTo(18.25);
        assertThat(actual.getLabel(0).getValue()).isEqualTo("east");
        assertThat(buffer.position()).isEqualTo(4);
    }
}
