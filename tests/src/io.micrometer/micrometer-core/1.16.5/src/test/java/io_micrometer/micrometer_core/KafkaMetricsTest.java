/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.Map;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaMetricsTest {

    @Test
    void bindsConsumerCountersAndGaugesWithoutBrokerTraffic() {
        Map<String, Object> properties = Map.of("bootstrap.servers", "127.0.0.1:9092",
                "client.id", "metrics-consumer", "group.id", "metrics-group",
                "enable.metrics.push", false);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(properties,
                        new ByteArrayDeserializer(), new ByteArrayDeserializer());
                KafkaClientMetrics metrics = new KafkaClientMetrics(consumer, Tags.of("application", "orders"))) {
            metrics.bindTo(registry);

            assertThat(registry.get("kafka.consumer.connection.count").tag("application", "orders")
                    .gauge().value()).isZero();
            assertThat(registry.get("kafka.consumer.coordinator.commit.total")
                    .functionCounter().count()).isZero();
            metrics.close();
            assertThat(registry.find("kafka.consumer.connection.count").gauge()).isNull();
        } finally {
            registry.close();
        }
    }
}
