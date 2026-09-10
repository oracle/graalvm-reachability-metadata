/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.StateRestoreListener;
import org.apache.kafka.test.MockClientSupplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises lifecycle configuration and safe pre-start KafkaStreams queries. */
public class KafkaStreamsApiCoverageTest {
    @Test
    void shouldConfigureLifecycleAndInspectAnUnstartedApplication() {
        Topology topology = topology();
        Properties properties = properties();
        KafkaStreams streams = new KafkaStreams(topology, properties, Time.SYSTEM);
        try {
            streams.setUncaughtExceptionHandler((Thread.UncaughtExceptionHandler) (thread, exception) -> { });
            streams.setUncaughtExceptionHandler(exception -> org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT);
            streams.setGlobalStateRestoreListener(new StateRestoreListener() {
                @Override public void onRestoreStart(org.apache.kafka.common.TopicPartition topicPartition, String storeName, long startOffset, long endOffset) { }
                @Override public void onBatchRestored(org.apache.kafka.common.TopicPartition topicPartition, String storeName, long batchEndOffset, long numRestored) { }
                @Override public void onRestoreEnd(org.apache.kafka.common.TopicPartition topicPartition, String storeName, long totalRestored) { }
            });
            assertThat(streams.metrics()).isNotNull();
            assertThat(streams.localThreadsMetadata()).isNotNull();
            assertThat(streams.metadataForLocalThreads()).isNotNull();
            assertThatThrownBy(streams::allMetadata).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> streams.allMetadataForStore("missing")).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> streams.streamsMetadataForStore("missing")).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> streams.queryMetadataForKey("missing", "key", Serdes.String().serializer())).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> streams.queryMetadataForKey("missing", "key", (org.apache.kafka.streams.processor.StreamPartitioner<String, Object>) (topic, key, value, numPartitions) -> 0)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> streams.store(org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                    "missing", org.apache.kafka.streams.state.QueryableStoreTypes.<String, String>keyValueStore()))).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(streams::metadataForAllStreamsClients).isInstanceOf(RuntimeException.class);
            assertThat(streams.allLocalStorePartitionLags()).isNotNull();
            streams.pause();
            assertThat(streams.isPaused()).isTrue();
            streams.resume();
            assertThat(streams.isPaused()).isFalse();
        } finally {
            streams.close(new KafkaStreams.CloseOptions().timeout(Duration.ZERO).leaveGroup(false));
        }
    }

    @Test
    void shouldStartAndStopWithTheMockClientSupplier() throws InterruptedException {
        MockClientSupplier supplier = mockClientSupplier();
        KafkaStreams streams = new KafkaStreams(topology(), properties(), supplier, Time.SYSTEM);
        try {
            streams.start();
            Thread.sleep(250L);
            assertThat(streams.state()).isNotEqualTo(KafkaStreams.State.CREATED);
            assertThat(streams.addStreamThread()).isPresent();
            assertThat(streams.removeStreamThread()).isPresent();
        } finally {
            streams.close(new KafkaStreams.CloseOptions().timeout(Duration.ofSeconds(5)).leaveGroup(false));
        }
    }

    @Test
    void shouldAcceptAllPublicConstructionFormsAndCloseOptions() {
        Topology topology = topology();
        Properties properties = properties();
        KafkaStreams streamsWithSupplier = new KafkaStreams(topology, properties(), mockClientSupplier(), Time.SYSTEM);
        KafkaStreams streamsWithConfig = new KafkaStreams(topology, new StreamsConfig(properties()), Time.SYSTEM);
        try {
            assertThat(new KafkaStreams.CloseOptions().timeout(Duration.ofMillis(1)).leaveGroup(true)).isNotNull();
            assertThat(streamsWithSupplier.state()).isEqualTo(KafkaStreams.State.CREATED);
            assertThat(streamsWithConfig.state()).isEqualTo(KafkaStreams.State.CREATED);
        } finally {
            streamsWithSupplier.close(Duration.ZERO);
            streamsWithConfig.close(Duration.ZERO);
        }
    }

    private static Topology topology() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("api-kafka-streams-input").to("api-kafka-streams-output");
        builder.globalTable("api-kafka-streams-global-input",
                org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()));
        return builder.build();
    }

    private static MockClientSupplier mockClientSupplier() {
        MockClientSupplier supplier = new MockClientSupplier();
        Node node = new Node(0, "localhost", 9092);
        Node controller = new Node(-1, "localhost", 9092);
        java.util.List<PartitionInfo> partitions = java.util.List.of(
                new PartitionInfo("api-kafka-streams-input", 0, node, new Node[] {node}, new Node[] {node}),
                new PartitionInfo("api-kafka-streams-global-input", 0, node, new Node[] {node}, new Node[] {node}));
        supplier.setCluster(new Cluster("api-cluster", java.util.List.of(node, controller), partitions,
                java.util.Set.of(), java.util.Set.of(), controller));
        supplier.consumer.updatePartitions("api-kafka-streams-input", java.util.List.of(partitions.get(0)));
        supplier.consumer.updateEndOffsets(java.util.Map.of(
                new org.apache.kafka.common.TopicPartition("api-kafka-streams-input", 0), 0L));
        supplier.restoreConsumer.updatePartitions("api-kafka-streams-global-input", java.util.List.of(partitions.get(1)));
        supplier.restoreConsumer.updateBeginningOffsets(java.util.Map.of(
                new org.apache.kafka.common.TopicPartition("api-kafka-streams-global-input", 0), 0L));
        supplier.restoreConsumer.updateEndOffsets(java.util.Map.of(
                new org.apache.kafka.common.TopicPartition("api-kafka-streams-global-input", 0), 0L));
        return supplier;
    }

    private static Properties properties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "api-kafka-streams-" + java.util.UUID.randomUUID());
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, "build/api-kafka-streams-" + java.util.UUID.randomUUID());
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
        return properties;
    }
}
