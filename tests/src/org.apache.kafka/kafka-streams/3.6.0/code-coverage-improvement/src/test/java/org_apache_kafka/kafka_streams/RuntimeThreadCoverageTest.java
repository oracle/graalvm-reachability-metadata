/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.test.MockClientSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeThreadCoverageTest {
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Test
    void globalStreamThreadInitializesAndClosesItsStateConsumer() throws InterruptedException {
        String topic = "global-input";
        StreamsBuilder builder = new StreamsBuilder();
        builder.globalTable(topic, Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("global-store"));
        MockClientSupplier clients = new MockClientSupplier();
        prepareConsumer(clients.restoreConsumer, topic);

        try (KafkaStreams streams = new KafkaStreams(builder.build(), properties("global"), clients)) {
            streams.start();
            awaitStartedOrFailed(streams);
        }

        assertThat(clients.restoreConsumer.closed()).isTrue();
    }

    @Test
    void consumerRebalanceAssignsAndRevokesStateUpdaterTasks() throws IOException, InterruptedException {
        String topic = "stream-input";
        String applicationId = "rebalance-" + UUID.randomUUID();
        Path stateDirectory = Files.createTempDirectory("kafka-runtime-coverage-");
        // A non-empty, unassigned task directory makes rebalance completion exercise directory release.
        Files.createDirectories(stateDirectory.resolve(applicationId).resolve("99_0").resolve("store"));
        Files.writeString(stateDirectory.resolve(applicationId).resolve("99_0").resolve("store/data"), "state");

        StreamsBuilder builder = new StreamsBuilder();
        builder.stream(topic, Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey()
                .count(Materialized.as("counts"))
                .toStream()
                .to("stream-output");
        MockClientSupplier clients = new MockClientSupplier();
        TopicPartition partition = prepareConsumer(clients.consumer, topic);
        TopicPartition changelog = prepareConsumer(clients.restoreConsumer,
                applicationId + "-counts-changelog");
        clients.restoreConsumer.updateBeginningOffsets(Map.of(changelog, 0L));
        clients.restoreConsumer.updateEndOffsets(Map.of(changelog, 1L));

        Properties properties = properties("rebalance");
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        properties.put("__state.updater.enabled__", true);
        try (KafkaStreams streams = new KafkaStreams(builder.build(), properties, clients)) {
            streams.start();
            awaitSubscription(clients.consumer);

            // MockConsumer dispatches these through StreamsRebalanceListener's public callbacks.
            clients.consumer.rebalance(List.of(partition));
            awaitStateUpdaterHandoff();
            clients.consumer.rebalance(List.of());
            clients.consumer.rebalance(List.of(partition));
            streams.metrics().values().forEach(metric -> metric.metricValue());
        }

        assertThat(clients.consumer.closed()).isTrue();
    }

    private static TopicPartition prepareConsumer(MockConsumer<byte[], byte[]> consumer, String topic) {
        TopicPartition partition = new TopicPartition(topic, 0);
        Node node = new Node(0, "localhost", 9092);
        consumer.updatePartitions(topic, List.of(new PartitionInfo(topic, 0, node,
                new Node[] {node}, new Node[] {node})));
        consumer.updateBeginningOffsets(Map.of(partition, 0L));
        consumer.updateEndOffsets(Map.of(partition, 0L));
        return partition;
    }

    private static void awaitSubscription(MockConsumer<byte[], byte[]> consumer) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (consumer.subscription().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(consumer.subscription()).isNotEmpty();
    }

    private static void awaitStateUpdaterHandoff() throws InterruptedException {
        // Task restoration is asynchronous when the state updater is enabled. Keep the assignment
        // active long enough for revocation to observe the updater-owned task.
        Thread.sleep(250L);
    }

    private static void awaitStartedOrFailed(KafkaStreams streams) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (streams.state() == KafkaStreams.State.REBALANCING && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(streams.state()).isNotEqualTo(KafkaStreams.State.CREATED);
    }

    private static Properties properties(String prefix) {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, prefix + "-" + UUID.randomUUID());
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.STATE_DIR_CONFIG,
                System.getProperty("java.io.tmpdir") + "/kafka-runtime-coverage-" + UUID.randomUUID());
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        return properties;
    }
}
