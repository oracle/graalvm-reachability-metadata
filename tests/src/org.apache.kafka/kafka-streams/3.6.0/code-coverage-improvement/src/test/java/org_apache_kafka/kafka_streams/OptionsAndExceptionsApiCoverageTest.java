/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.DefaultProductionExceptionHandler;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.errors.TaskAssignmentException;
import org.apache.kafka.streams.errors.TaskIdFormatException;
import org.apache.kafka.streams.errors.TopologyException;
import org.apache.kafka.streams.internals.StreamsConfigUtils;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises option objects, enum contracts, and exception handling behavior. */
public class OptionsAndExceptionsApiCoverageTest {
    @Test
    void shouldConfigureStreamAndTableJoinOptions() {
        StreamJoined<String, String, Integer> joined = StreamJoined.with(
                Serdes.String(), Serdes.String(), Serdes.Integer());
        assertThat(StreamJoined.<String, String, Integer>with(
                Stores.inMemoryWindowStore("left-supplier", Duration.ofSeconds(2), Duration.ofSeconds(1), false),
                Stores.inMemoryWindowStore("right-supplier", Duration.ofSeconds(2), Duration.ofSeconds(1), false)))
                .isNotNull();
        StreamJoined<String, String, Integer> configured = joined.withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                .withOtherValueSerde(Serdes.Integer()).withStoreName("join-store")
                .withThisStoreSupplier(Stores.inMemoryWindowStore("this", Duration.ofSeconds(1), Duration.ofSeconds(1), false))
                .withOtherStoreSupplier(Stores.inMemoryWindowStore("other", Duration.ofSeconds(1), Duration.ofSeconds(1), false))
                .withLoggingEnabled(Map.of("retention.ms", "1")).withLoggingDisabled()
                .withName("join-name");
        assertThat(configured).isNotNull();
        assertThat(configured.toString()).contains("join-store");

        org.apache.kafka.streams.processor.StreamPartitioner<String, Void> partitioner = (topic, key, value, partitions) -> 0;
        TableJoined<String, String> tableJoined = TableJoined.with(partitioner, partitioner)
                .withPartitioner(partitioner).withOtherPartitioner(partitioner).withName("table-name");
        assertThat(tableJoined).isNotNull();
        assertThat(TableJoined.<String, String>as("table").withName("renamed")).isNotNull();
    }

    @Test
    void shouldConfigureSuppressionBuffersAndEnumerations() {
        assertThat(Suppressed.BufferConfig.maxBytes(1024).withMaxRecords(10).emitEarlyWhenFull()).isNotNull();
        assertThat(Suppressed.BufferConfig.maxRecords(10).withMaxBytes(1024).withLoggingDisabled()).isNotNull();
        assertThat(Suppressed.BufferConfig.unbounded().withNoBound().withLoggingEnabled(Map.of())).isNotNull();
        assertThat(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded())).isNotNull();
        assertThat(Topology.AutoOffsetReset.valueOf("EARLIEST")).isEqualTo(Topology.AutoOffsetReset.EARLIEST);
        assertThat(Topology.AutoOffsetReset.values()).isNotEmpty();
        assertThat(StreamsConfigUtils.ProcessingMode.valueOf("AT_LEAST_ONCE")).isNotNull();
        assertThat(StreamsConfigUtils.ProcessingMode.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.KafkaStreams.State.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse.values()).isNotEmpty();
        assertThat(ProductionExceptionHandler.ProductionExceptionHandlerResponse.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.kstream.EmitStrategy.StrategyType.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.kstream.Materialized.StoreType.values()).isNotEmpty();
        assertThat(WindowedSerdes.sessionWindowedSerdeFrom(String.class)).isNotNull();
    }

    @Test
    void shouldPreserveExceptionCausesAndUseProductionHandlerDefault() {
        Exception cause = new IllegalArgumentException("bad record");
        assertThat(new TopologyException("message").getMessage()).contains("message");
        assertThat(new TopologyException("message", cause).getCause()).isSameAs(cause);
        assertThat(new TopologyException(cause).getCause()).isSameAs(cause);
        assertThat(new TaskAssignmentException(cause).getCause()).isSameAs(cause);
        assertThat(new TaskIdFormatException(cause).getCause()).isSameAs(cause);
        DefaultProductionExceptionHandler handler = new DefaultProductionExceptionHandler();
        assertThat(handler.handleSerializationException(new ProducerRecord<>("topic", "key", "value"), cause))
                .isEqualTo(ProductionExceptionHandler.ProductionExceptionHandlerResponse.FAIL);
        KafkaClientSupplier supplier = new KafkaClientSupplier() {
            @Override public org.apache.kafka.clients.producer.Producer<byte[], byte[]> getProducer(Map<String, Object> config) {
                return null;
            }
            @Override public org.apache.kafka.clients.consumer.Consumer<byte[], byte[]> getConsumer(Map<String, Object> config) {
                return null;
            }
            @Override public org.apache.kafka.clients.consumer.Consumer<byte[], byte[]> getRestoreConsumer(Map<String, Object> config) {
                return null;
            }
            @Override public org.apache.kafka.clients.consumer.Consumer<byte[], byte[]> getGlobalConsumer(Map<String, Object> config) {
                return null;
            }
        };
        assertThatThrownBy(() -> supplier.getAdmin(Map.of())).isInstanceOf(UnsupportedOperationException.class);
    }
}
