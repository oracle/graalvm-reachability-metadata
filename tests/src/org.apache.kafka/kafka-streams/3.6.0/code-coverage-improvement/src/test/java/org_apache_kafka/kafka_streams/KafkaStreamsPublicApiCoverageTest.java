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
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.errors.BrokerNotFoundException;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.errors.InvalidStateStorePartitionException;
import org.apache.kafka.streams.errors.LockException;
import org.apache.kafka.streams.errors.MissingSourceTopicException;
import org.apache.kafka.streams.errors.ProcessorStateException;
import org.apache.kafka.streams.errors.StateStoreMigratedException;
import org.apache.kafka.streams.errors.StateStoreNotAvailableException;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.errors.StreamsNotStartedException;
import org.apache.kafka.streams.errors.StreamsRebalancingException;
import org.apache.kafka.streams.errors.StreamsStoppedException;
import org.apache.kafka.streams.errors.TaskAssignmentException;
import org.apache.kafka.streams.errors.TaskCorruptedException;
import org.apache.kafka.streams.errors.TaskIdFormatException;
import org.apache.kafka.streams.errors.TaskMigratedException;
import org.apache.kafka.streams.errors.TopologyException;
import org.apache.kafka.streams.errors.UnknownStateStoreException;
import org.apache.kafka.streams.errors.UnknownTopologyException;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaStreamsPublicApiCoverageTest {

    @Test
    void exposesValueObjectsAndStoreQueryOptions() {
        KeyValue<String, Integer> pair = KeyValue.pair("region", 3);
        assertThat(pair).isEqualTo(new KeyValue<>("region", 3));
        assertThat(pair.hashCode()).isEqualTo(new KeyValue<>("region", 3).hashCode());
        assertThat(pair.toString()).contains("region").contains("3");

        HostInfo active = new HostInfo("active", 9092);
        HostInfo standby = new HostInfo("standby", 9093);
        KeyQueryMetadata metadata = new KeyQueryMetadata(active, Set.of(standby), 4);
        assertThat(metadata.activeHost()).isEqualTo(active);
        assertThat(metadata.getActiveHost()).isEqualTo(active);
        assertThat(metadata.partition()).isEqualTo(4);
        assertThat(metadata.getPartition()).isEqualTo(4);
        assertThat(metadata.standbyHosts()).containsExactly(standby);
        assertThat(metadata.getStandbyHosts()).containsExactly(standby);
        assertThat(metadata).isEqualTo(new KeyQueryMetadata(active, Set.of(standby), 4));
        assertThat(metadata.hashCode()).isEqualTo(new KeyQueryMetadata(active, Set.of(standby), 4).hashCode());
        assertThat(metadata.toString()).contains("active").contains("4");

        StoreQueryParameters<?> query = StoreQueryParameters
                .fromNameAndType("counts", QueryableStoreTypes.keyValueStore())
                .withPartition(2)
                .enableStaleStores();
        assertThat(query.storeName()).isEqualTo("counts");
        assertThat(query.queryableStoreType()).isInstanceOf(QueryableStoreTypes.keyValueStore().getClass());
        assertThat(query.partition()).isEqualTo(2);
        assertThat(query.staleStoresEnabled()).isTrue();
        assertThat(query).isEqualTo(query);
        assertThat(query.hashCode()).isEqualTo(query.hashCode());
        assertThat(query.toString()).contains("counts").contains("2");
    }

    @Test
    void configuresTopologyAndStreamsLifecycleBeforeStartup() {
        Properties properties = streamProperties();
        StreamsConfig config = new StreamsConfig(properties);
        assertThat(StreamsConfig.consumerPrefix("client")).isEqualTo("consumer.client");
        assertThat(StreamsConfig.mainConsumerPrefix("client")).isEqualTo("main.consumer.client");
        assertThat(StreamsConfig.restoreConsumerPrefix("client")).isEqualTo("restore.consumer.client");
        assertThat(StreamsConfig.globalConsumerPrefix("client")).isEqualTo("global.consumer.client");
        assertThat(StreamsConfig.producerPrefix("client")).isEqualTo("producer.client");
        assertThat(StreamsConfig.adminClientPrefix("client")).isEqualTo("admin.client");
        assertThat(StreamsConfig.clientTagPrefix("client")).isEqualTo("client.tag.client");
        assertThat(StreamsConfig.configDef().names()).contains(StreamsConfig.APPLICATION_ID_CONFIG);
        assertThat(config.getGlobalConsumerConfigs("global")).containsEntry("client.id", "global-global-consumer");
        assertThat(config.defaultTimestampExtractor()).isNotNull();
        assertThat(config.defaultDeserializationExceptionHandler()).isNotNull();

        TopologyConfig topologyConfig = new TopologyConfig(config);
        assertThat(topologyConfig.isNamedTopology()).isFalse();
        assertThat(topologyConfig.parseStoreType()).isNotNull();
        Topology topology = new Topology(topologyConfig).addSource("source", "input").addSink("sink", "output", "source");
        assertThat(topology.describe().subtopologies()).hasSize(1);

        StreamsBuilder builder = new StreamsBuilder(topologyConfig);
        builder.stream(java.util.regex.Pattern.compile("input-.*"));
        builder.stream(java.util.regex.Pattern.compile("events-.*"), org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()));
        builder.globalTable("global-topic");
        builder.globalTable("global-topic-2", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()));
        assertThat(builder.build().describe().globalStores()).hasSize(2);

        try (KafkaStreams streams = new KafkaStreams(topology, properties, Time.SYSTEM)) {
            assertThat(streams.state()).isEqualTo(KafkaStreams.State.CREATED);
            assertThat(streams.isPaused()).isFalse();
            streams.pause();
            assertThat(streams.isPaused()).isTrue();
            streams.resume();
            assertThat(streams.isPaused()).isFalse();
            assertThatThrownBy(streams::allMetadata).isInstanceOf(StreamsNotStartedException.class);
            assertThatThrownBy(() -> streams.allMetadataForStore("missing")).isInstanceOf(StreamsNotStartedException.class);
            assertThatThrownBy(() -> streams.streamsMetadataForStore("missing")).isInstanceOf(StreamsNotStartedException.class);
            assertThat(streams.localThreadsMetadata()).hasSize(1);
            assertThat(streams.metadataForLocalThreads()).hasSize(1);
            assertThatThrownBy(streams::metadataForAllStreamsClients).isInstanceOf(StreamsNotStartedException.class);
            assertThat(streams.metrics()).isNotEmpty();
            assertThat(streams.allLocalStorePartitionLags()).isEmpty();
            assertThatThrownBy(() -> streams.queryMetadataForKey("missing", "key", Serdes.String().serializer()))
                    .isInstanceOf(StreamsNotStartedException.class);
            assertThat(streams.removeStreamThread()).isEmpty();
            assertThat(streams.removeStreamThread(Duration.ofMillis(1))).isEmpty();
            assertThat(streams.close(new KafkaStreams.CloseOptions().timeout(Duration.ofMillis(1)).leaveGroup(true))).isFalse();
        }
        assertThat(KafkaStreams.State.RUNNING.isRunningOrRebalancing()).isTrue();
        assertThat(KafkaStreams.State.PENDING_SHUTDOWN.hasStartedOrFinishedShuttingDown()).isTrue();
    }

    @Test
    void preservesExceptionMessagesCausesAndTaskContext() {
        RuntimeException cause = new RuntimeException("connection lost");
        TaskId task = new TaskId(2, 7);
        assertThat(new BrokerNotFoundException("broker", cause)).hasMessage("broker").hasCause(cause);
        assertThat(new BrokerNotFoundException(cause)).hasCause(cause);
        assertThat(new InvalidStateStoreException("store", cause)).hasCause(cause);
        assertThat(new InvalidStateStorePartitionException("partition", cause)).hasCause(cause);
        assertThat(new LockException("lock", cause)).hasCause(cause);
        assertThat(new ProcessorStateException("processor", cause)).hasCause(cause);
        assertThat(new StateStoreMigratedException("migrated", cause)).hasCause(cause);
        assertThat(new StateStoreNotAvailableException("unavailable", cause)).hasCause(cause);
        assertThat(new StreamsNotStartedException("not started", cause)).hasCause(cause);
        assertThat(new StreamsRebalancingException("rebalancing", cause)).hasCause(cause);
        assertThat(new StreamsStoppedException("stopped", cause)).hasCause(cause);
        assertThat(new TaskAssignmentException("assignment", cause)).hasCause(cause);
        assertThat(new TaskIdFormatException("task", cause)).hasCause(cause);
        assertThat(new TaskMigratedException("migrated", cause)).hasCause(cause);
        assertThat(new TopologyException("topology", cause)).hasCause(cause);
        assertThat(new UnknownStateStoreException("store", cause)).hasCause(cause);
        assertThat(new UnknownTopologyException("topology", cause, "named")).hasCause(cause);
        assertThat(new MissingSourceTopicException("topic")).hasMessage("topic");
        assertThat(new StreamsException("task failed", cause, task).taskId()).contains(task);
        StreamsException exception = new StreamsException(cause);
        exception.setTaskId(task);
        assertThat(exception.taskId()).contains(task);
        assertThat(new TaskCorruptedException(Set.of(task)).corruptedTasks()).containsExactly(task);
    }

    private Properties streamProperties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "coverage-app");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir") + "/kafka-coverage-state");
        return properties;
    }
}
