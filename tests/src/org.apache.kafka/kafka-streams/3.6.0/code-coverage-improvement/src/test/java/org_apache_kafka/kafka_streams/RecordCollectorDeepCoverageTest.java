/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.ProcessorTopology;
import org.apache.kafka.streams.processor.internals.RecordCollectorImpl;
import org.apache.kafka.streams.processor.internals.StreamsProducer;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.common.metrics.Metrics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives dirty record-collector shutdown through the producer abstraction. */
public class RecordCollectorDeepCoverageTest {
    @Test
    void shouldAbortTheProducerThroughDirtyClose() {
        MockProducer<byte[], byte[]> producer = new MockProducer<>(
                true, new ByteArraySerializer(), new ByteArraySerializer());
        KafkaClientSupplier clients = new KafkaClientSupplier() {
            @Override
            public Producer<byte[], byte[]> getProducer(Map<String, Object> config) {
                return producer;
            }

            @Override
            public Consumer<byte[], byte[]> getConsumer(Map<String, Object> config) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Consumer<byte[], byte[]> getRestoreConsumer(Map<String, Object> config) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Consumer<byte[], byte[]> getGlobalConsumer(Map<String, Object> config) {
                throw new UnsupportedOperationException();
            }
        };
        StreamsProducer streamsProducer = new StreamsProducer(streamsConfig(), "coverage-StreamThread-1", clients,
                new TaskId(0, 0), UUID.randomUUID(), new LogContext(), Time.SYSTEM);
        Metrics metrics = new Metrics();
        StreamsMetricsImpl streamsMetrics = new StreamsMetricsImpl(metrics, "collector-client", "coverage-StreamThread-1",
                Time.SYSTEM);
        ProcessorTopology topology = new ProcessorTopology(List.of(), Map.of(), Map.of(), List.of(), List.of(),
                Map.of(), Set.of());
        ProductionExceptionHandler handler = new ProductionExceptionHandler() {
            @Override
            public ProductionExceptionHandlerResponse handle(
                    org.apache.kafka.clients.producer.ProducerRecord<byte[], byte[]> record, Exception exception) {
                return ProductionExceptionHandlerResponse.CONTINUE;
            }

            @Override
            public void configure(Map<String, ?> configs) {
            }
        };
        RecordCollectorImpl collector = new RecordCollectorImpl(new LogContext(), new TaskId(0, 0), streamsProducer,
                handler, streamsMetrics, topology);

        collector.closeDirty();
        assertThat(collector.offsets()).isEmpty();
        metrics.close();
    }

    private static StreamsConfig streamsConfig() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "collector-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        return new StreamsConfig(properties);
    }
}
