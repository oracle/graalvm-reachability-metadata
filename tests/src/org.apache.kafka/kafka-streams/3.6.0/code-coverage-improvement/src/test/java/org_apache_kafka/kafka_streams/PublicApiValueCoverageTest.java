/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.apache.kafka.streams.errors.BrokerNotFoundException;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
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
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.EmitStrategy;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.UnlimitedWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.processor.TaskId;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the small value objects and configuration factories as a user would. */
public class PublicApiValueCoverageTest {
    @Test
    void shouldUseValueObjectsAndQueryParameters() {
        KeyValue<String, Integer> pair = KeyValue.pair("one", 1);
        assertThat(pair).isEqualTo(KeyValue.pair("one", 1));
        assertThat(pair.hashCode()).isEqualTo(KeyValue.pair("one", 1).hashCode());
        assertThat(pair.toString()).contains("one");

        Windowed<String> window = new Windowed<>("key", new org.apache.kafka.streams.kstream.internals.TimeWindow(1, 2));
        assertThat(window.hashCode()).isEqualTo(new Windowed<>("key", new org.apache.kafka.streams.kstream.internals.TimeWindow(1, 2)).hashCode());

        HostInfoHolder hosts = new HostInfoHolder();
        KeyQueryMetadata metadata = new KeyQueryMetadata(hosts.active, Set.of(hosts.standby), 3);
        assertThat(metadata.activeHost()).isEqualTo(hosts.active);
        assertThat(metadata.getActiveHost()).isEqualTo(hosts.active);
        assertThat(metadata.standbyHosts()).containsExactly(hosts.standby);
        assertThat(metadata.getStandbyHosts()).containsExactly(hosts.standby);
        assertThat(metadata.partition()).isEqualTo(3);
        assertThat(metadata.getPartition()).isEqualTo(3);
        assertThat(metadata).isEqualTo(new KeyQueryMetadata(hosts.active, Set.of(hosts.standby), 3));
        assertThat(metadata.toString()).contains("partition=3");

        StoreQueryParameters<org.apache.kafka.streams.state.ReadOnlyKeyValueStore<String, String>> params = StoreQueryParameters.fromNameAndType(
                "store", org.apache.kafka.streams.state.QueryableStoreTypes.<String, String>keyValueStore());
        assertThat(params.storeName()).isEqualTo("store");
        assertThat(params.queryableStoreType()).isNotNull();
        assertThat(params.partition()).isNull();
        assertThat(params.staleStoresEnabled()).isFalse();
        assertThat(params.enableStaleStores().staleStoresEnabled()).isTrue();
        assertThat(params.withPartition(2).partition()).isEqualTo(2);
        assertThat(params.toString()).contains("store");
        assertThat(params.toString()).contains("store", "staleStores=false");

        assertThat(org.apache.kafka.streams.state.QueryableStoreTypes.keyValueStore()).isNotNull();
        assertThat(org.apache.kafka.streams.state.QueryableStoreTypes.sessionStore()).isNotNull();
        assertThat(org.apache.kafka.streams.state.QueryableStoreTypes.windowStore()).isNotNull();
        assertThat(org.apache.kafka.streams.state.QueryableStoreTypes.timestampedKeyValueStore()).isNotNull();
        assertThat(org.apache.kafka.streams.state.QueryableStoreTypes.timestampedWindowStore()).isNotNull();
    }

    @Test
    void shouldBuildNamedWindowAndSuppressionOptions() {
        assertThat(Named.as("named").withName("renamed")).isNotNull();
        assertThat(Branched.<String, String>as("branch").withName("renamed")).isNotNull();
        assertThat(Consumed.with(Serdes.String(), Serdes.String()).withName("input").withName("renamed")).isNotNull();
        assertThat(Grouped.with(Serdes.String(), Serdes.String()).withName("group").withName("renamed")).isNotNull();
        assertThat(Joined.with(Serdes.String(), Serdes.String(), Serdes.String()).withName("join").withName("renamed")).isNotNull();
        assertThat(Joined.with(Serdes.String(), Serdes.String(), Serdes.String(), "named-join")).isNotNull();
        assertThat(Produced.with(Serdes.String(), Serdes.String()).withName("output").withName("renamed")).isNotNull();
        assertThat(Repartitioned.with(Serdes.String(), Serdes.String()).withName("repartition").withName("renamed")).isNotNull();
        assertThat(StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()).withName("stream-join").withName("renamed")).isNotNull();
        assertThat(TableJoined.as("table-join").withName("renamed")).isNotNull();
        assertThat(Printed.<String, String>toSysOut().withName("print").withName("renamed")).isNotNull();
        assertThat(Suppressed.untilTimeLimit(Duration.ofSeconds(1), Suppressed.BufferConfig.unbounded()).withName("suppressed-name")).isNotNull();

        assertThat(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(2)).inactivityGap()).isEqualTo(Duration.ofSeconds(2).toMillis());
        assertThat(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)).timeDifferenceMs()).isEqualTo(Duration.ofSeconds(2).toMillis());
        assertThat(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2)).size()).isEqualTo(Duration.ofSeconds(2).toMillis());
        assertThat(JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)).size()).isEqualTo(Duration.ofSeconds(2).toMillis() * 2);
        assertThat(UnlimitedWindows.of()).isNotNull();
        assertThat(EmitStrategy.onWindowClose()).isNotNull();
        assertThat(EmitStrategy.StrategyType.ON_WINDOW_CLOSE.forType(EmitStrategy.StrategyType.ON_WINDOW_CLOSE)).isNotNull();
        assertThat(Suppressed.BufferConfig.unbounded()).isNotNull();
        assertThat(Materialized.<String, String, org.apache.kafka.streams.state.KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(Materialized.StoreType.IN_MEMORY)).isNotNull();
        assertThat(Materialized.<String, String>as(StoresSupplierHolder.supplier())).isNotNull();
        assertThat(WindowedSerdes.timeWindowedSerdeFrom(String.class)).isNotNull();
        assertThat(WindowedSerdes.timeWindowedSerdeFrom(String.class, 1000L)).isNotNull();
    }

    @Test
    void shouldReadStreamsConfigurationAndTaskIdentifiers() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "api-values");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        StreamsConfig config = new StreamsConfig(properties);
        assertThat(config.configDef()).isNotNull();
        assertThat(config.consumerPrefix("x")).isEqualTo("consumer.x");
        assertThat(config.producerPrefix("x")).isEqualTo("producer.x");
        assertThat(config.adminClientPrefix("x")).isEqualTo("admin.x");
        assertThat(config.mainConsumerPrefix("x")).isEqualTo("main.consumer.x");
        assertThat(config.restoreConsumerPrefix("x")).isEqualTo("restore.consumer.x");
        assertThat(config.globalConsumerPrefix("x")).isEqualTo("global.consumer.x");
        assertThat(config.clientTagPrefix("x")).isEqualTo("client.tag.x");
        assertThat(config.defaultTimestampExtractor()).isNotNull();
        assertThat(config.defaultDeserializationExceptionHandler()).isNotNull();
        assertThat(config.getGlobalConsumerConfigs("client")).isNotEmpty();

        TaskId id = new TaskId(4, 7);
        ByteBuffer buffer = ByteBuffer.allocate(32);
        id.writeTo(buffer, 0);
        buffer.flip();
        assertThat(TaskId.readFrom(buffer, 0)).isEqualTo(id);

        assertThat(org.apache.kafka.streams.KafkaStreams.State.valueOf("RUNNING").isRunningOrRebalancing()).isTrue();
        assertThat(org.apache.kafka.streams.KafkaStreams.State.values()).contains(org.apache.kafka.streams.KafkaStreams.State.NOT_RUNNING);
        assertThat(new TopologyConfig(new StreamsConfig(properties)).isNamedTopology()).isFalse();
        assertThat(Topology.AutoOffsetReset.values()).isNotEmpty();
    }

    @Test
    void shouldRoundTripProtocolAndLifecycleEnumValues() {
        assertEnumRoundTrip(org.apache.kafka.streams.KafkaStreams.State.values(), org.apache.kafka.streams.KafkaStreams.State.class);
        assertEnumRoundTrip(Topology.AutoOffsetReset.values(), Topology.AutoOffsetReset.class);
        assertEnumRoundTrip(org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse.values(),
                org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse.class);
        assertEnumRoundTrip(org.apache.kafka.streams.errors.ProductionExceptionHandler.ProductionExceptionHandlerResponse.values(),
                org.apache.kafka.streams.errors.ProductionExceptionHandler.ProductionExceptionHandlerResponse.class);
        assertEnumRoundTrip(org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.values(),
                org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.class);
        assertEnumRoundTrip(org.apache.kafka.streams.internals.StreamsConfigUtils.ProcessingMode.values(),
                org.apache.kafka.streams.internals.StreamsConfigUtils.ProcessingMode.class);
        assertEnumRoundTrip(org.apache.kafka.streams.internals.UpgradeFromValues.values(),
                org.apache.kafka.streams.internals.UpgradeFromValues.class);
        assertEnumRoundTrip(EmitStrategy.StrategyType.values(), EmitStrategy.StrategyType.class);
        assertEnumRoundTrip(Materialized.StoreType.values(), Materialized.StoreType.class);
        assertEnumRoundTrip(org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionWrapper.Instruction.values(),
                org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionWrapper.Instruction.class);
        assertEnumRoundTrip(org.apache.kafka.streams.kstream.internals.suppress.BufferFullStrategy.values(),
                org.apache.kafka.streams.kstream.internals.suppress.BufferFullStrategy.class);
        assertEnumRoundTrip(org.apache.kafka.streams.processor.PunctuationType.values(),
                org.apache.kafka.streams.processor.PunctuationType.class);
        assertEnumRoundTrip(org.apache.kafka.streams.processor.internals.GlobalStreamThread.State.values(),
                org.apache.kafka.streams.processor.internals.GlobalStreamThread.State.class);
        assertEnumRoundTrip(org.apache.kafka.streams.processor.internals.StreamThread.State.values(),
                org.apache.kafka.streams.processor.internals.StreamThread.State.class);
        assertEnumRoundTrip(org.apache.kafka.streams.processor.internals.Task.State.values(),
                org.apache.kafka.streams.processor.internals.Task.State.class);
        assertEnumRoundTrip(org.apache.kafka.streams.processor.internals.Task.TaskType.values(),
                org.apache.kafka.streams.processor.internals.Task.TaskType.class);
        assertEnumRoundTrip(org.apache.kafka.streams.processor.internals.assignment.AssignorError.values(),
                org.apache.kafka.streams.processor.internals.assignment.AssignorError.class);
    }

    @Test
    void shouldRenderTheStreamsConfigurationDocumentation() {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        try {
            System.setOut(new java.io.PrintStream(output));
            StreamsConfig.main(new String[0]);
        } finally {
            System.setOut(original);
        }
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("streamsconfigs_application.id");
    }

    private static <E extends Enum<E>> void assertEnumRoundTrip(E[] values, Class<E> type) {
        assertThat(values).isNotEmpty();
        for (E value : values) {
            assertThat(Enum.valueOf(type, value.name())).isSameAs(value);
        }
    }

    @Test
    void shouldPreserveExceptionCausesAndTaskIds() {
        TaskId task = new TaskId(1, 2);
        StreamsException[] exceptions = {
                new StreamsException("message"), new StreamsException(new IllegalArgumentException()),
                new StreamsException("message", new IllegalArgumentException()),
                new StreamsException("message", task), new StreamsException(new IllegalArgumentException(), task),
                new StreamsException("message", new IllegalArgumentException(), task)
        };
        for (StreamsException exception : exceptions) {
            exception.setTaskId(task);
            assertThat(exception.taskId()).contains(task);
        }
        assertThat(new TaskCorruptedException(Set.of(task)).corruptedTasks()).contains(task);
        assertThat(new TaskCorruptedException(Set.of(task), new OffsetOutOfRangeException(Map.of())).corruptedTasks()).contains(task);
        assertThat(new MissingSourceTopicException("missing")).hasMessage("missing");
        assertThat(new TaskMigratedException("migrated")).hasMessageContaining("migrated");
        assertThat(new BrokerNotFoundException("x")).hasMessage("x");
        assertThat(new InvalidStateStoreException("x")).hasMessage("x");
        assertThat(new LockException("x")).hasMessage("x");
        assertThat(new ProcessorStateException("x")).hasMessage("x");
        assertThat(new StateStoreMigratedException("x")).hasMessage("x");
        assertThat(new StateStoreNotAvailableException("x")).hasMessage("x");
        assertThat(new StreamsNotStartedException("x")).hasMessage("x");
        assertThat(new StreamsRebalancingException("x")).hasMessage("x");
        assertThat(new StreamsStoppedException("x")).hasMessage("x");
        assertThat(new TaskAssignmentException("x")).hasMessage("x");
        assertThat(new TaskIdFormatException("x")).hasMessageContaining("x");
    }

    private static final class HostInfoHolder {
        private final HostInfo active = new HostInfo("active", 1);
        private final HostInfo standby = new HostInfo("standby", 2);
    }

    private static final class StoresSupplierHolder {
        private static org.apache.kafka.streams.state.KeyValueBytesStoreSupplier supplier() {
            return org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore("store");
        }
    }
}
