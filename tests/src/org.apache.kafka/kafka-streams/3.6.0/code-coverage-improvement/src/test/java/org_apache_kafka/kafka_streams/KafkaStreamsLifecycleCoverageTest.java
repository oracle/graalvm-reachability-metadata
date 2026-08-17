/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsNotStartedException;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.processor.StateRestoreListener;
import org.apache.kafka.streams.processor.StreamPartitioner;
import org.apache.kafka.streams.processor.internals.DefaultKafkaClientSupplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class KafkaStreamsLifecycleCoverageTest {
    @Test
    @SuppressWarnings("deprecation")
    void exposesPreStartLifecycleMetadataAndQueryContracts() {
        Topology topology = topology();
        Properties properties = properties("lifecycle");
        try (KafkaStreams streams = new KafkaStreams(topology, properties, Time.SYSTEM)) {
            assertThat(streams.state()).isEqualTo(KafkaStreams.State.CREATED);
            assertThat(streams.metrics()).isNotEmpty();
            assertThat(streams.localThreadsMetadata()).hasSize(1);
            assertThat(streams.metadataForLocalThreads()).hasSize(1);
            assertThat(streams.allLocalStorePartitionLags()).isEmpty();

            streams.setGlobalStateRestoreListener(mock(StateRestoreListener.class));
            streams.setUncaughtExceptionHandler(exception ->
                    StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT);
            streams.pause();
            assertThat(streams.isPaused()).isTrue();
            streams.resume();
            assertThat(streams.isPaused()).isFalse();

            assertThatThrownBy(streams::allMetadata).isInstanceOf(StreamsNotStartedException.class);
            assertThatThrownBy(() -> streams.allMetadataForStore("missing"))
                    .isInstanceOf(StreamsNotStartedException.class);
            assertThatThrownBy(() -> streams.streamsMetadataForStore("missing"))
                    .isInstanceOf(StreamsNotStartedException.class);
            assertThatThrownBy(streams::metadataForAllStreamsClients)
                    .isInstanceOf(StreamsNotStartedException.class);
            assertThatThrownBy(() -> streams.queryMetadataForKey(
                    "missing", "key", Serdes.String().serializer()))
                    .isInstanceOf(StreamsNotStartedException.class);
            StreamPartitioner<String, String> partitioner = (topic, key, value, partitions) -> 0;
            assertThatThrownBy(() -> streams.queryMetadataForKey("missing", "key", partitioner))
                    .isInstanceOf(StreamsNotStartedException.class);
            assertThatThrownBy(() -> streams.store(org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    "missing", org.apache.kafka.streams.state.QueryableStoreTypes.keyValueStore())))
                    .isInstanceOf(StreamsNotStartedException.class);

            assertThat(streams.removeStreamThread()).isEmpty();
            assertThat(streams.removeStreamThread(Duration.ofMillis(1))).isEmpty();
            assertThat(streams.close(new KafkaStreams.CloseOptions().timeout(Duration.ofMillis(1)).leaveGroup(true)))
                    .isFalse();
        }
    }

    @Test
    void publicConstructorVariantsCreateEquivalentUnstartedClients() {
        Topology topology = topology();
        Properties properties = properties("constructors");
        try (KafkaStreams supplied = new KafkaStreams(topology, properties,
                new DefaultKafkaClientSupplier(), Time.SYSTEM);
             KafkaStreams configured = new KafkaStreams(topology, new StreamsConfig(properties), Time.SYSTEM)) {
            assertThat(supplied.state()).isEqualTo(KafkaStreams.State.CREATED);
            assertThat(configured.state()).isEqualTo(KafkaStreams.State.CREATED);
            assertThat(supplied.metrics()).isNotEmpty();
            assertThat(configured.metrics()).isNotEmpty();
        }
    }

    private static Topology topology() {
        return new Topology().addSource("source", "input").addSink("sink", "output", "source");
    }

    private static Properties properties(String prefix) {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, prefix + "-" + UUID.randomUUID());
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.STATE_DIR_CONFIG,
                System.getProperty("java.io.tmpdir") + "/kafka-coverage-" + UUID.randomUUID());
        properties.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);
        return properties;
    }
}
