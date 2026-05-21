/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.telemetry.internals.ClientTelemetryUtils;
import org.apache.kafka.common.telemetry.internals.MetricKey;
import org.apache.kafka.common.telemetry.internals.SinglePointMetric;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Metric;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.MetricsData;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufUnsafeUtilTest {

    @Test
    void telemetryMetricsDataRoundTripsThroughKafkaProtobufSerialization() throws Exception {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("client_id", "native-image-test");
        tags.put("node_id", "1");
        SinglePointMetric pointMetric = SinglePointMetric.gauge(
                new MetricKey("kafka.producer.request.latency.avg", tags),
                Long.valueOf(42L),
                Instant.parse("2024-01-01T00:00:00Z"),
                Collections.emptySet());

        MetricsData metricsData = MetricsData.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .addMetrics(pointMetric.builder().build())
                                .build())
                        .build())
                .build();

        ByteBuffer serializedMetrics = ClientTelemetryUtils.compress(metricsData, CompressionType.NONE);
        MetricsData parsedMetrics = ClientTelemetryUtils.deserializeMetricsData(serializedMetrics);

        Metric parsedMetric = parsedMetrics.getResourceMetrics(0).getScopeMetrics(0).getMetrics(0);
        assertThat(parsedMetrics.getResourceMetricsCount()).isEqualTo(1);
        assertThat(parsedMetric.getName()).isEqualTo("kafka.producer.request.latency.avg");
        assertThat(parsedMetric.getGauge().getDataPoints(0).getAsInt()).isEqualTo(42L);
        assertThat(parsedMetric.getGauge().getDataPoints(0).getAttributesList())
                .extracting(attribute -> attribute.getKey() + "=" + attribute.getValue().getStringValue())
                .containsExactly("client_id=native-image-test", "node_id=1");
    }
}
