/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.Materialized;
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
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.processor.StreamPartitioner;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowBytesStoreSupplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DslConfigurationCoverageTest {
    private static final Serde<String> STRING = Serdes.String();
    private static final StreamPartitioner<String, String> PARTITIONER =
            (topic, key, value, partitions) -> Math.floorMod(key.hashCode(), partitions);

    @Test
    void joinedAndStreamJoinedFluentOptionsRetainUsableConfiguration() {
        Joined<String, String, String> joined = Joined.<String, String, String>as("join")
                .withKeySerde(STRING).withValueSerde(STRING).withOtherValueSerde(STRING)
                .withGracePeriod(Duration.ofSeconds(3)).withName("renamed");
        assertThat(joined.keySerde()).isSameAs(STRING);
        assertThat(joined.valueSerde()).isSameAs(STRING);
        assertThat(joined.otherValueSerde()).isSameAs(STRING);
        assertThat(joined.gracePeriod()).isEqualTo(Duration.ofSeconds(3));
        assertThat(Joined.with(STRING, STRING, STRING)).isNotNull();
        assertThat(Joined.with(STRING, STRING, STRING, "named")).isNotNull();
        assertThat(Joined.with(STRING, STRING, STRING, "named", Duration.ZERO)).isNotNull();
        assertThat(Joined.<String, String, String>keySerde(STRING).keySerde()).isSameAs(STRING);
        assertThat(Joined.<String, String, String>valueSerde(STRING).valueSerde()).isSameAs(STRING);
        assertThat(Joined.<String, String, String>otherValueSerde(STRING).otherValueSerde()).isSameAs(STRING);

        WindowBytesStoreSupplier left = Stores.inMemoryWindowStore("left", Duration.ofMinutes(1),
                Duration.ofSeconds(10), false);
        WindowBytesStoreSupplier right = Stores.inMemoryWindowStore("right", Duration.ofMinutes(1),
                Duration.ofSeconds(10), false);
        StreamJoined<String, String, String> streamJoined = StreamJoined.<String, String, String>with(left, right)
                .withKeySerde(STRING).withValueSerde(STRING).withOtherValueSerde(STRING)
                .withThisStoreSupplier(left).withOtherStoreSupplier(right).withStoreName("stores")
                .withLoggingEnabled(Map.of("retention.ms", "1000")).withLoggingDisabled().withName("joined");
        assertThat(streamJoined.toString()).contains("joined");
        assertThat(StreamJoined.<String, String, String>as("named")).isNotNull();
    }

    @Test
    void sourceGroupingSinkAndRepartitionOptionsSupportFluentComposition() {
        FailOnInvalidTimestamp extractor = new FailOnInvalidTimestamp();
        Consumed<String, String> consumed = Consumed.<String, String>as("input")
                .withKeySerde(STRING).withValueSerde(STRING).withTimestampExtractor(extractor)
                .withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST).withName("renamed");
        assertThat(consumed).isEqualTo(Consumed.with(STRING, STRING, extractor, Topology.AutoOffsetReset.EARLIEST)
                .withName("renamed"));
        assertThat(Consumed.<String, String>with(extractor)).isNotNull();
        assertThat(Consumed.<String, String>with(Topology.AutoOffsetReset.LATEST)).isNotNull();

        assertThat(Grouped.<String, String>as("group").withKeySerde(STRING).withValueSerde(STRING)
                .withName("renamed")).isNotNull();
        assertThat(Grouped.<String, String>keySerde(STRING)).isNotNull();
        assertThat(Grouped.<String, String>valueSerde(STRING)).isNotNull();
        assertThat(Grouped.with("group", STRING, STRING)).isNotNull();

        Produced<String, String> produced = Produced.<String, String>as("output")
                .withKeySerde(STRING).withValueSerde(STRING).withStreamPartitioner(PARTITIONER).withName("renamed");
        assertThat(produced).isEqualTo(Produced.with(STRING, STRING, PARTITIONER).withName("renamed"));
        assertThat(Produced.<String, String>keySerde(STRING)).isNotNull();
        assertThat(Produced.<String, String>valueSerde(STRING)).isNotNull();
        assertThat(Produced.streamPartitioner(PARTITIONER)).isNotNull();

        Repartitioned<String, String> repartitioned = Repartitioned.<String, String>with(STRING, STRING)
                .withNumberOfPartitions(3).withStreamPartitioner(PARTITIONER).withName("repartition");
        assertThat(repartitioned).isNotNull();
        assertThat(Repartitioned.<String, String>numberOfPartitions(2)).isNotNull();
        assertThat(Repartitioned.streamPartitioner(PARTITIONER)).isNotNull();
    }

    @Test
    void materializationSuppressionAndTableJoinOptionsCompose() {
        Materialized<String, String, ?> materialized = Materialized.<String, String, org.apache.kafka.streams.processor.StateStore>as(
                Materialized.StoreType.IN_MEMORY)
                .withCachingEnabled().withCachingDisabled().withLoggingEnabled(new HashMap<>(Map.of("cleanup.policy", "compact")))
                .withLoggingDisabled().withRetention(Duration.ofMinutes(1)).withStoreType(Materialized.StoreType.ROCKS_DB);
        assertThat(materialized.storeType).isEqualTo(Materialized.StoreType.ROCKS_DB);
        assertThat(Materialized.StoreType.values()).contains(Materialized.StoreType.IN_MEMORY);
        assertThat(Materialized.StoreType.valueOf("ROCKS_DB")).isEqualTo(Materialized.StoreType.ROCKS_DB);
        assertThat(Materialized.as(Stores.inMemoryWindowStore("window", Duration.ofMinutes(1), Duration.ofSeconds(5), false))).isNotNull();
        assertThat(Materialized.as(Stores.inMemorySessionStore("session", Duration.ofMinutes(1)))).isNotNull();

        Suppressed.BufferConfig<?> eager = Suppressed.BufferConfig.maxRecords(2).withMaxBytes(100)
                .withLoggingEnabled(Map.of("cleanup.policy", "compact")).withLoggingDisabled();
        assertThat(eager).isNotNull();
        assertThat(Suppressed.BufferConfig.maxBytes(10).withMaxRecords(3)).isNotNull();
        assertThat(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()).withName("final-results"))
                .isNotNull();

        StreamPartitioner<String, Void> tablePartitioner = (topic, key, value, count) -> 0;
        TableJoined<String, String> tableJoined = TableJoined.<String, String>as("table-join")
                .withPartitioner(tablePartitioner).withOtherPartitioner(tablePartitioner).withName("renamed");
        assertThat(tableJoined).isNotNull();
        assertThat(TableJoined.with(tablePartitioner, tablePartitioner)).isNotNull();
    }

    @Test
    void windowDefinitionsHaveStableValueSemanticsAndExpectedRanges() {
        JoinWindows join = JoinWindows.of(Duration.ofSeconds(2)).before(Duration.ofSeconds(1))
                .after(Duration.ofSeconds(3)).grace(Duration.ofMillis(1));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> join.windowsFor(10))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(join).isEqualTo(join).hasSameHashCodeAs(join);
        assertThat(join.toString()).contains("JoinWindows");

        TimeWindows time = TimeWindows.of(Duration.ofSeconds(4)).advanceBy(Duration.ofSeconds(2))
                .grace(Duration.ofMillis(2));
        assertThat(time.windowsFor(5000)).isNotEmpty();
        assertThat(time).isEqualTo(time).hasSameHashCodeAs(time);
        assertThat(time.toString()).contains("TimeWindows");

        UnlimitedWindows unlimited = UnlimitedWindows.of().startOn(Instant.ofEpochMilli(5));
        assertThat(unlimited.windowsFor(6)).hasSize(1);
        assertThat(unlimited.size()).isEqualTo(Long.MAX_VALUE);
        assertThat(unlimited.gracePeriodMs()).isZero();
        assertThat(unlimited).isEqualTo(unlimited).hasSameHashCodeAs(unlimited);
        assertThat(unlimited.toString()).contains("UnlimitedWindows");

        SessionWindows session = SessionWindows.with(Duration.ofSeconds(3)).grace(Duration.ofMillis(1));
        assertThat(session).isEqualTo(session).hasSameHashCodeAs(session);
        assertThat(session.toString()).contains("SessionWindows");
        SlidingWindows sliding = SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(2), Duration.ofMillis(1));
        assertThat(sliding).isEqualTo(sliding).hasSameHashCodeAs(sliding);
        assertThat(sliding.toString()).contains("SlidingWindows");

        TimeWindow window = new TimeWindow(3, 8);
        assertThat(window.end()).isEqualTo(8);
        assertThat(window.startTime()).isEqualTo(Instant.ofEpochMilli(3));
        assertThat(window.endTime()).isEqualTo(Instant.ofEpochMilli(8));
        assertThat(window).isEqualTo(new TimeWindow(3, 8));
        assertThat(window.toString()).contains("3");
        assertThat(new Windowed<>("key", window)).isEqualTo(new Windowed<>("key", new TimeWindow(3, 8)));
        assertThat(new Windowed<>("key", window).toString()).contains("key");
    }
}
