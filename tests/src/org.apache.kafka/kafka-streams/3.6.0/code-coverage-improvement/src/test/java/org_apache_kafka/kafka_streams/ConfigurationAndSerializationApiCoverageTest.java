/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.BrokerNotFoundException;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.errors.InvalidStateStorePartitionException;
import org.apache.kafka.streams.errors.LockException;
import org.apache.kafka.streams.errors.ProcessorStateException;
import org.apache.kafka.streams.errors.StateStoreMigratedException;
import org.apache.kafka.streams.errors.StateStoreNotAvailableException;
import org.apache.kafka.streams.errors.StreamsNotStartedException;
import org.apache.kafka.streams.errors.StreamsRebalancingException;
import org.apache.kafka.streams.errors.StreamsStoppedException;
import org.apache.kafka.streams.errors.TaskAssignmentException;
import org.apache.kafka.streams.errors.TaskIdFormatException;
import org.apache.kafka.streams.errors.TaskMigratedException;
import org.apache.kafka.streams.errors.UnknownStateStoreException;
import org.apache.kafka.streams.errors.UnknownTopologyException;
import org.apache.kafka.streams.internals.UpgradeFromValues;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedSerializer;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises independent public configuration objects and wire-format adapters. */
public class ConfigurationAndSerializationApiCoverageTest {
    @Test
    void shouldConfigureSerdeAndTopologyOptions() {
        assertThat(Joined.with(Serdes.String(), Serdes.String(), Serdes.Integer(), "join", Duration.ofSeconds(2))
                .withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                .withOtherValueSerde(Serdes.Integer()).withGracePeriod(Duration.ofSeconds(3))
                .withName("renamed").gracePeriod()).isEqualTo(Duration.ofSeconds(3));
        assertThat(Joined.<String, String, Integer>keySerde(Serdes.String()).keySerde()).isNotNull();
        assertThat(Joined.<String, String, Integer>valueSerde(Serdes.String()).valueSerde()).isNotNull();
        assertThat(Joined.<String, String, Integer>otherValueSerde(Serdes.Integer()).otherValueSerde()).isNotNull();
        assertThat(Joined.<String, String, Integer>as("named")).isNotNull();

        Consumed<String, String> consumed = Consumed.with(Serdes.String(), Serdes.String())
                .withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                .withTimestampExtractor((record, previous) -> record.timestamp())
                .withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST).withName("input");
        assertThat(consumed).isEqualTo(consumed);
        assertThat(Consumed.<String, String>as("source")).isNotNull();
        assertThat(Consumed.with(Topology.AutoOffsetReset.LATEST)).isNotNull();
        assertThat(Consumed.with((record, previous) -> record.timestamp())).isNotNull();

        assertThat(Grouped.<String, String>as("group").withKeySerde(Serdes.String()).withValueSerde(Serdes.String()).withName("g")).isNotNull();
        assertThat(Grouped.<String, String>keySerde(Serdes.String()).withName("k")).isNotNull();
        assertThat(Grouped.<String, String>valueSerde(Serdes.String()).withName("v")).isNotNull();
        assertThat(Grouped.with("named", Serdes.String(), Serdes.String())).isNotNull();

        assertThat(Repartitioned.<String, String>as("r").withNumberOfPartitions(2)
                .withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                .withStreamPartitioner((topic, key, value, partitions) -> 0).withName("r2")).isNotNull();
        assertThat(Repartitioned.<String, String>numberOfPartitions(2)).isNotNull();
        assertThat(Repartitioned.<String, String>streamPartitioner((topic, key, value, partitions) -> 0)).isNotNull();
        assertThat(Produced.<String, String>as("p").withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                .withStreamPartitioner((topic, key, value, partitions) -> 0).withName("p2")).isNotNull();
        assertThat(Produced.<String, String>keySerde(Serdes.String())).isNotNull();
        assertThat(Produced.<String, String>valueSerde(Serdes.String())).isNotNull();
        assertThat(Produced.<String, String>streamPartitioner((topic, key, value, partitions) -> 0)).isNotNull();
    }

    @Test
    void shouldConfigureMaterializationBranchesPrintingAndWindows() {
        Materialized<String, String, org.apache.kafka.streams.state.KeyValueStore<Bytes, byte[]>> materialized =
                Materialized.with(Serdes.String(), Serdes.String());
        assertThat(materialized.withCachingDisabled().withCachingEnabled().withLoggingDisabled()
                .withLoggingEnabled(Map.of("retention.ms", "1000"))
                .withRetention(Duration.ofSeconds(10)).withStoreType(Materialized.StoreType.ROCKS_DB)).isSameAs(materialized);
        assertThat(Materialized.<String, String>as(Stores.inMemoryWindowStore("window", Duration.ofSeconds(1), Duration.ofSeconds(1), false))).isNotNull();
        assertThat(Materialized.<String, String>as(Stores.inMemorySessionStore("session", Duration.ofSeconds(1)))).isNotNull();

        assertThat(Branched.<String, String>withConsumer(stream -> assertThat(stream).isNotNull())).isNotNull();
        assertThat(Branched.<String, String>withConsumer(stream -> { }, "branch")).isNotNull();
        assertThat(Branched.<String, String>withFunction(stream -> stream, "mapped")).isNotNull();
        assertThat(Branched.<String, String>withFunction(stream -> stream)).isNotNull();
        assertThat(Printed.<String, String>toFile("build/api-print.txt")
                .withKeyValueMapper((key, value) -> key + value).withName("print")).isNotNull();

        JoinWindows windows = JoinWindows.of(Duration.ofSeconds(2)).before(Duration.ofSeconds(1))
                .after(Duration.ofSeconds(3)).grace(Duration.ofSeconds(4));
        assertThat(windows.size()).isEqualTo(Duration.ofSeconds(4).toMillis());
        assertThat(windows).isEqualTo(JoinWindows.ofTimeDifferenceAndGrace(Duration.ofSeconds(2), Duration.ofSeconds(4))
                .before(Duration.ofSeconds(1)).after(Duration.ofSeconds(3)));
        assertThat(windows.hashCode()).isEqualTo(windows.hashCode());
        assertThat(windows.toString()).contains("beforeMs");
        assertThat(Stores.lruMap("cache", 4).name()).isEqualTo("cache");
        assertThat(Stores.persistentVersionedKeyValueStore("versions", Duration.ofSeconds(5)).name()).isEqualTo("versions");
    }

    @Test
    void shouldRoundTripSessionKeysAndUsePublicHashUtilities() {
        Windowed<String> key = new Windowed<>("session", new SessionWindow(10, 20));
        SessionWindowedSerializer<String> serializer = new SessionWindowedSerializer<>(Serdes.String().serializer());
        serializer.configure(Map.of(), false);
        byte[] encoded = serializer.serialize("topic", key);
        assertThat(serializer.serializeBaseKey("topic", key)).isNotEmpty();
        SessionWindowedDeserializer<String> deserializer = new SessionWindowedDeserializer<>(Serdes.String().deserializer());
        deserializer.configure(Map.of(), false);
        assertThat(deserializer.deserialize("topic", encoded)).isEqualTo(key);
        serializer.close();
        deserializer.close();
        assertThat(new SessionWindowedSerializer<String>()).isNotNull();
        assertThat(new SessionWindowedDeserializer<String>()).isNotNull();
        assertThat(new WindowedSerdes.TimeWindowedSerde<String>().forChangelog(true)).isNotNull();
        assertThat(new WindowedSerdes.TimeWindowedSerde<String>()).isNotNull();

        assertThat(org.apache.kafka.streams.state.internals.Murmur3.hash32(new byte[] {1, 2, 3})).isNotZero();
        assertThat(org.apache.kafka.streams.state.internals.Murmur3.hash32(1L)).isNotZero();
        assertThat(org.apache.kafka.streams.state.internals.Murmur3.hash32(1L, 2L)).isNotZero();
        assertThat(org.apache.kafka.streams.state.internals.Murmur3.hash64(new byte[] {1, 2, 3})).isNotZero();
        assertThat(new FailOnInvalidTimestamp().extract(new org.apache.kafka.clients.consumer.ConsumerRecord<Object, Object>(
                "topic", 0, 1L, 12L, TimestampType.CREATE_TIME, -1L, -1, -1, "k", "v"), 0L)).isEqualTo(12L);
        assertThat(org.apache.kafka.streams.internals.ApiUtils.validateMillisecondInstant(Instant.ofEpochMilli(12), "timestamp")).isEqualTo(12L);
        assertThat(UpgradeFromValues.values()).isNotEmpty();
        assertThat(UpgradeFromValues.valueOf(UpgradeFromValues.values()[0].name())).isNotNull();
    }

    @Test
    void shouldPreserveExceptionCauseInformation() {
        Throwable cause = new IllegalStateException("cause");
        assertThat(new BrokerNotFoundException("broker", cause).getCause()).isSameAs(cause);
        assertThat(new BrokerNotFoundException(cause).getCause()).isSameAs(cause);
        assertThat(new InvalidStateStoreException("store", cause).getCause()).isSameAs(cause);
        assertThat(new InvalidStateStoreException(cause).getCause()).isSameAs(cause);
        assertThat(new InvalidStateStorePartitionException("partition").getMessage()).contains("partition");
        assertThat(new InvalidStateStorePartitionException("partition", cause).getCause()).isSameAs(cause);
        assertThat(new LockException("lock", cause).getCause()).isSameAs(cause);
        assertThat(new LockException(cause).getCause()).isSameAs(cause);
        assertThat(new ProcessorStateException("state", cause).getCause()).isSameAs(cause);
        assertThat(new ProcessorStateException(cause).getCause()).isSameAs(cause);
        assertThat(new StateStoreMigratedException("migrated", cause).getCause()).isSameAs(cause);
        assertThat(new StateStoreNotAvailableException("unavailable", cause).getCause()).isSameAs(cause);
        assertThat(new StreamsNotStartedException("not-started", cause).getCause()).isSameAs(cause);
        assertThat(new StreamsRebalancingException("rebalancing", cause).getCause()).isSameAs(cause);
        assertThat(new StreamsStoppedException("stopped", cause).getCause()).isSameAs(cause);
        assertThat(new TaskAssignmentException("assignment", cause).getCause()).isSameAs(cause);
        assertThat(new TaskIdFormatException("format", cause).getCause()).isSameAs(cause);
        assertThat(new TaskMigratedException("migrated", cause).getCause()).isSameAs(cause);
        assertThat(new UnknownStateStoreException("unknown", cause).getCause()).isSameAs(cause);
        assertThat(new UnknownStateStoreException("unknown").getMessage()).contains("unknown");
        assertThat(new UnknownTopologyException("topology", "detail").getMessage()).contains("detail");
        assertThat(new UnknownTopologyException("topology", cause, "detail").getCause()).isSameAs(cause);
    }
}
