/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.EmitStrategy;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KGroupedTable;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicDslCoverageTest {
    @Test
    void streamOperationsBuildAndExecuteARealTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> left = builder.stream(Pattern.compile("dsl-input"));
        KStream<String, String> right = builder.stream("dsl-right", Consumed.with(Serdes.String(), Serdes.String()));

        left.mapValues((org.apache.kafka.streams.kstream.ValueMapper<String, String>) String::toUpperCase,
                        Named.as("upper"))
                .flatMapValues(value -> java.util.List.of(value, value + "!"), Named.as("expand"))
                .to((key, value, context) -> "dsl-output", Produced.with(Serdes.String(), Serdes.String()));
        left.groupBy((key, value) -> value, Grouped.with(Serdes.String(), Serdes.String())).count();
        left.join(right, (a, b) -> a + b, JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)));
        left.join(right, (key, a, b) -> key + a + b,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)));
        left.leftJoin(right, (a, b) -> a + b,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()));
        left.repartition();
        left.repartition(Repartitioned.<String, String>as("named-repartition")
                .withKeySerde(Serdes.String()).withValueSerde(Serdes.String()));
        Map<String, KStream<String, String>> branches = left.split(Named.as("split-"))
                .branch((key, value) -> value.startsWith("a"), Branched.withFunction(stream -> stream, "a"))
                .branch((key, value) -> value.startsWith("b"), Branched.withConsumer(stream -> { }, "b"))
                .noDefaultBranch();
        left.toTable();
        left.toTable(Named.as("table"));
        left.toTable(Materialized.as("table-store"));
        left.toTable(Named.as("named-table"), Materialized.as("named-table-store"));

        Topology topology = builder.build();
        assertThat(branches).containsKey("split-a").hasSize(1);
        assertThat(topology.describe().toString()).contains("upper", "expand", "named-repartition");
    }

    @Test
    void groupedTablesAndWindowedStreamsExposeAllAggregationForms() {
        StreamsBuilder builder = new StreamsBuilder();
        KGroupedTable<String, Long> grouped = builder.table("table-input",
                Consumed.with(Serdes.String(), Serdes.Long()))
                .groupBy((key, value) -> org.apache.kafka.streams.KeyValue.pair(key, value),
                        Grouped.with(Serdes.String(), Serdes.Long()));
        grouped.count(Named.as("group-count"));
        grouped.reduce(Long::sum, (a, b) -> a - b);
        grouped.reduce(Long::sum, (a, b) -> a - b, Materialized.as("reduce-store"));
        grouped.reduce(Long::sum, (a, b) -> a - b, Named.as("named-reduce"), Materialized.as("named-reduce-store"));
        grouped.aggregate(() -> 0L, (key, value, aggregate) -> aggregate + value,
                (key, value, aggregate) -> aggregate - value);
        grouped.aggregate(() -> 0L, (key, value, aggregate) -> aggregate + value,
                (key, value, aggregate) -> aggregate - value, Named.as("named-aggregate"));
        grouped.aggregate(() -> 0L, (key, value, aggregate) -> aggregate + value,
                (key, value, aggregate) -> aggregate - value, Materialized.as("aggregate-store"));
        grouped.aggregate(() -> 0L, (key, value, aggregate) -> aggregate + value,
                (key, value, aggregate) -> aggregate - value, Named.as("both-aggregate"),
                Materialized.as("both-aggregate-store"));

        KStream<String, Long> stream = builder.stream("window-input", Consumed.with(Serdes.String(), Serdes.Long()));
        stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5)))
                .emitStrategy(EmitStrategy.onWindowUpdate()).aggregate(() -> 0L, (key, value, total) -> total + value);
        stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(6)))
                .aggregate(() -> 0L, (key, value, total) -> total + value, Named.as("time-aggregate"));
        stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(7)))
                .count(Materialized.as("time-count"));
        stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5)))
                .emitStrategy(EmitStrategy.onWindowClose()).reduce(Long::sum);
        stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(6)))
                .reduce(Long::sum, Named.as("slide-reduce"), Materialized.as("slide-store"));
        stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(5)))
                .emitStrategy(EmitStrategy.onWindowClose()).count();
        stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(6)))
                .aggregate(() -> 0L, (key, value, total) -> total + value,
                        (key, one, two) -> one + two, Named.as("session-aggregate"),
                        Materialized.<String, Long, org.apache.kafka.streams.state.SessionStore<org.apache.kafka.common.utils.Bytes, byte[]>>as("session-store"));

        String description = builder.build().describe().toString();
        assertThat(description).contains("group-count", "named-reduce", "named-aggregate", "time-aggregate",
                "slide-reduce", "session-aggregate");
    }

    @Test
    void builderGlobalStoresAndNamedOptionsAreUsable() {
        StreamsBuilder builder = new StreamsBuilder();
        assertThat(builder.globalTable("global-one").queryableStoreName()).isNull();
        assertThat(builder.globalTable("global-two", Consumed.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(builder.globalTable("global-three", Materialized.as("global-three-store")).queryableStoreName())
                .isEqualTo("global-three-store");
        assertThat(builder.globalTable("global-four", Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("global-four-store")).queryableStoreName()).isEqualTo("global-four-store");
        builder.addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("extra-store"),
                Serdes.String(), Serdes.String()));
        assertThat(Consumed.with(Serdes.String(), Serdes.String()).withName("consumed")).isNotNull();
        assertThat(Grouped.with(Serdes.String(), Serdes.String()).withName("grouped")).isNotNull();
        assertThat(Produced.with(Serdes.String(), Serdes.String()).withName("produced")).isNotNull();
        assertThat(Named.as("first").withName("second")).isNotNull();
        assertThat(Suppressed.untilTimeLimit(Duration.ofSeconds(1), Suppressed.BufferConfig.unbounded())
                .withName("suppression")).isNotNull();
        assertThat(builder.build().describe().globalStores()).hasSize(4);
    }
}
