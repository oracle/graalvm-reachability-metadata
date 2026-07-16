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
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.BrokerNotFoundException;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.errors.InvalidStateStorePartitionException;
import org.apache.kafka.streams.errors.LockException;
import org.apache.kafka.streams.errors.ProcessorStateException;
import org.apache.kafka.streams.errors.StateStoreMigratedException;
import org.apache.kafka.streams.errors.StateStoreNotAvailableException;
import org.apache.kafka.streams.errors.StreamsRebalancingException;
import org.apache.kafka.streams.errors.StreamsStoppedException;
import org.apache.kafka.streams.errors.TaskAssignmentException;
import org.apache.kafka.streams.errors.TaskIdFormatException;
import org.apache.kafka.streams.errors.TaskMigratedException;
import org.apache.kafka.streams.errors.TopologyException;
import org.apache.kafka.streams.errors.UnknownStateStoreException;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedSerializer;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaStreamsOptionsCoverageTest {

    @Test
    void configuresDslValueObjectsAndPreservesTheirOptions() {
        org.apache.kafka.common.serialization.Serde<String> stringSerde = Serdes.String();
        Joined<String, String, String> joined = Joined.with(stringSerde, stringSerde, stringSerde,
                "orders", Duration.ofSeconds(2)).withKeySerde(stringSerde).withValueSerde(stringSerde)
                .withOtherValueSerde(stringSerde).withName("renamed").withGracePeriod(Duration.ofSeconds(3));
        assertThat(joined.keySerde()).isSameAs(stringSerde);
        assertThat(joined.valueSerde()).isSameAs(stringSerde);
        assertThat(joined.otherValueSerde()).isSameAs(stringSerde);
        assertThat(joined.gracePeriod()).isEqualTo(Duration.ofSeconds(3));
        assertThat(Joined.<String, String, String>as("join")).isNotNull();

        Produced<String, String> produced = Produced.<String, String>as("output").withKeySerde(Serdes.String())
                .withValueSerde(Serdes.String()).withStreamPartitioner((topic, key, value, partitions) -> 0)
                .withName("named-output");
        assertThat(produced).isEqualTo(produced);
        assertThat(produced.hashCode()).isEqualTo(produced.hashCode());
        assertThat(Produced.<String, String>with(Serdes.String(), Serdes.String())
                .withKeySerde(stringSerde).withValueSerde(stringSerde)
                .withStreamPartitioner((topic, key, value, partitions) -> 0)).isNotNull();

        Repartitioned<String, String> repartitioned = Repartitioned.with(Serdes.String(), Serdes.String())
                .withName("repartitioned").withNumberOfPartitions(2).withKeySerde(Serdes.String())
                .withValueSerde(Serdes.String()).withStreamPartitioner((topic, key, value, partitions) -> 0);
        assertThat(repartitioned).isNotNull();
        assertThat(Repartitioned.<String, String>as("named")).isNotNull();
        assertThat(Repartitioned.<String, String>numberOfPartitions(3)).isNotNull();
        assertThat(Repartitioned.<String, String>streamPartitioner((topic, key, value, partitions) -> 0)).isNotNull();

        Materialized<String, String, org.apache.kafka.streams.processor.StateStore> materialized =
                Materialized.<String, String, org.apache.kafka.streams.processor.StateStore>as("counts")
                .withKeySerde(Serdes.String()).withValueSerde(Serdes.String()).withCachingEnabled()
                .withCachingDisabled().withLoggingEnabled(Map.of("cleanup.policy", "compact"))
                .withRetention(Duration.ofHours(1));
        assertThat(materialized).isNotNull();
        assertThat(Materialized.as(Stores.persistentKeyValueStore("other"))).isNotNull();

        assertThat(Printed.<String, String>toSysOut().withLabel("orders").withName("printer")
                .withKeyValueMapper((key, value) -> key + "=" + value)).isNotNull();
        assertThat(Printed.<String, String>toFile(System.getProperty("java.io.tmpdir") + "/orders.txt")).isNotNull();

        JoinWindows windows = JoinWindows.of(Duration.ofSeconds(5)).grace(Duration.ofSeconds(1));
        assertThat(windows.gracePeriodMs()).isEqualTo(1000L);
        assertThatThrownBy(() -> windows.windowsFor(10L)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(windows).isEqualTo(JoinWindows.of(Duration.ofSeconds(5)).grace(Duration.ofSeconds(1)));
        assertThat(windows.toString()).contains("JoinWindows");

        SessionWindows sessions = SessionWindows.with(Duration.ofSeconds(4)).grace(Duration.ofSeconds(1));
        assertThat(sessions.inactivityGap()).isEqualTo(4000L);
        assertThat(sessions.gracePeriodMs()).isEqualTo(1000L);
        assertThat(sessions).isEqualTo(SessionWindows.with(Duration.ofSeconds(4)).grace(Duration.ofSeconds(1)));
        assertThat(SessionWindows.ofInactivityGapAndGrace(Duration.ofSeconds(4), Duration.ofSeconds(1))).isNotNull();
        assertThat(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(4)).toString()).contains("SessionWindows");

        SlidingWindows sliding = SlidingWindows.ofTimeDifferenceAndGrace(Duration.ofSeconds(4), Duration.ofSeconds(1));
        assertThat(sliding.timeDifferenceMs()).isEqualTo(4000L);
        assertThat(sliding.gracePeriodMs()).isEqualTo(1000L);
        assertThat(sliding).isEqualTo(SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(4), Duration.ofSeconds(1)));
        assertThat(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(4)).toString()).contains("SlidingWindows");
    }

    @Test
    void roundTripsSessionWindowedKeysAndBuildsStatefulTopologies() {
        Windowed<String> key = new Windowed<>("customer", new SessionWindow(10L, 20L));
        SessionWindowedSerializer<String> serializer = new SessionWindowedSerializer<>(Serdes.String().serializer());
        SessionWindowedDeserializer<String> deserializer = new SessionWindowedDeserializer<>(Serdes.String().deserializer());
        serializer.configure(Map.of(), true);
        deserializer.configure(Map.of(), true);
        byte[] bytes = serializer.serialize("sessions", key);
        assertThat(serializer.serializeBaseKey("sessions", key)).isNotEmpty();
        assertThat(deserializer.deserialize("sessions", bytes)).isEqualTo(key);
        serializer.close();
        deserializer.close();

        StreamsBuilder builder = new StreamsBuilder();
        builder.addStateStore(Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore("counts"),
                Serdes.String(), Serdes.Long()));
        builder.table("orders", Materialized.as("orders-store"));
        builder.globalTable("catalog", Materialized.as("catalog-store"));
        assertThat(builder.build().describe().globalStores()).hasSize(1);

        StreamJoined<String, String, String> streamJoined = StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String())
                .withKeySerde(Serdes.String()).withValueSerde(Serdes.String()).withOtherValueSerde(Serdes.String())
                .withName("stream-join").withStoreName("join-store").withLoggingDisabled()
                .withLoggingEnabled(Map.of("cleanup.policy", "compact"));
        assertThat(streamJoined.toString()).contains("join-store");
        assertThat(StreamJoined.<String, String, String>as("named-join")).isNotNull();
        assertThat(StreamJoined.<String, String, String>with(
                Stores.persistentWindowStore("left", Duration.ofHours(1), Duration.ofMinutes(1), false),
                Stores.persistentWindowStore("right", Duration.ofHours(1), Duration.ofMinutes(1), false))
                .withThisStoreSupplier(Stores.persistentWindowStore("left-replacement", Duration.ofHours(1), Duration.ofMinutes(1), false))
                .withOtherStoreSupplier(Stores.persistentWindowStore("right-replacement", Duration.ofHours(1), Duration.ofMinutes(1), false)))
                .isNotNull();
    }

    @Test
    void reportsConfigurationErrorsAndLifecycleRulesWithContext() {
        RuntimeException cause = new RuntimeException("lost");
        assertThat(new BrokerNotFoundException("broker")).hasMessage("broker");
        assertThat(new InvalidStateStoreException(cause)).hasCause(cause);
        assertThat(new InvalidStateStorePartitionException("partition")).hasMessage("partition");
        assertThat(new LockException("lock")).hasMessage("lock");
        assertThat(new LockException(cause)).hasCause(cause);
        assertThat(new ProcessorStateException("processor")).hasMessage("processor");
        assertThat(new ProcessorStateException(cause)).hasCause(cause);
        assertThat(new StateStoreMigratedException("migrated")).hasMessage("migrated");
        assertThat(new StateStoreNotAvailableException("unavailable")).hasMessage("unavailable");
        assertThat(new StreamsRebalancingException("rebalancing")).hasMessage("rebalancing");
        assertThat(new StreamsStoppedException("stopped")).hasMessage("stopped");
        assertThat(new TaskAssignmentException("assignment")).hasMessage("assignment");
        assertThat(new TaskAssignmentException(cause)).hasCause(cause);
        assertThat(new TaskIdFormatException("task")).hasMessageContaining("task");
        assertThat(new TaskIdFormatException(cause)).hasCause(cause);
        assertThat(new TaskMigratedException("migrated")).hasMessageContaining("migrated");
        assertThat(new TopologyException("topology")).hasMessageContaining("topology");
        assertThat(new TopologyException(cause)).hasCause(cause);
        assertThat(new UnknownStateStoreException("store")).hasMessage("store");

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "coverage-options");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir") + "/coverage-options");
        Topology topology = new Topology().addSource("source", "input").addSink("sink", "output", "source");
        try (KafkaStreams streams = new KafkaStreams(topology, properties, Time.SYSTEM)) {
            streams.setUncaughtExceptionHandler((Thread.UncaughtExceptionHandler) (thread, exception) -> { });
            streams.setUncaughtExceptionHandler(exception -> org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
                    .StreamThreadExceptionResponse.SHUTDOWN_CLIENT);
            streams.setGlobalStateRestoreListener(new org.apache.kafka.streams.processor.StateRestoreListener() {
                @Override
                public void onRestoreStart(org.apache.kafka.common.TopicPartition partition, String store,
                                           long startOffset, long endOffset) {
                }

                @Override
                public void onBatchRestored(org.apache.kafka.common.TopicPartition partition, String store,
                                            long batchEndOffset, long numRestored) {
                }

                @Override
                public void onRestoreEnd(org.apache.kafka.common.TopicPartition partition, String store,
                                         long totalRestored) {
                }
            });
            assertThat(streams.addStreamThread()).isEmpty();
        }
    }
}
