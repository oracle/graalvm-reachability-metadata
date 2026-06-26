/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageLite;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
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
    void computesSchemaForUnregisteredLiteMessageUsingUnsafeAllocationFallback() {
        LiteMessage message = LiteMessage.getDefaultInstance();

        assertThat(message.getSerializedSize()).isZero();
        assertThat(message.isInitialized()).isTrue();
    }

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

    public static final class LiteMessage extends GeneratedMessageLite<LiteMessage, LiteMessage.Builder> {

        private static final LiteMessage DEFAULT_INSTANCE = new LiteMessage();
        private static volatile Parser<LiteMessage> PARSER;

        private LiteMessage() {
        }

        public static LiteMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object argument0, Object argument1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new LiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case BUILD_MESSAGE_INFO:
                    return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", null);
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_PARSER:
                    Parser<LiteMessage> localParser = PARSER;
                    if (localParser == null) {
                        localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                        PARSER = localParser;
                    }
                    return localParser;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case SET_MEMOIZED_IS_INITIALIZED:
                    return null;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<LiteMessage, Builder> {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
