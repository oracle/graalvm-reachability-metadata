/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Gauge;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Metric;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.MetricsData;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufUnsafeUtilTest {

    @Test
    void serializesAndParsesTelemetryPayloadUsingShadedProtobufRuntime() throws Exception {
        MetricsData metricsData = MetricsData.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .addMetrics(Metric.newBuilder()
                                        .setName("native-image-test-metric")
                                        .setGauge(Gauge.newBuilder()
                                                .addDataPoints(NumberDataPoint.newBuilder()
                                                        .setAsDouble(42.0))))))
                .build();

        byte[] serialized = metricsData.toByteArray();
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(serialized.length);
        directBuffer.put(serialized);
        directBuffer.flip();

        MetricsData parsed = MetricsData.parseFrom(directBuffer);

        assertThat(serialized).isNotEmpty();
        assertThat(parsed).isEqualTo(metricsData);
        assertThat(parsed.getSerializedSize()).isEqualTo(serialized.length);
    }
}
