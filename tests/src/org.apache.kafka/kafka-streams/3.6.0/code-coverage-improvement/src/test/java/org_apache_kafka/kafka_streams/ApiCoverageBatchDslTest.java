/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.CogroupedKStream;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the public stream, table, cogroup, and window scenarios behind internal DSL nodes. */
public class ApiCoverageBatchDslTest {
    @Test
    void shouldExerciseUncoveredStreamOverloadsWithAJoinedTable() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> stream = builder.stream("batch-dsl-stream", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> other = builder.stream("batch-dsl-other", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table("batch-dsl-table", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()));
        JoinWindows windows = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2));
        StreamJoined<String, String, String> joined = StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String());
        Materialized<String, String, KeyValueStore<Bytes, byte[]>> materialized =
                Materialized.with(Serdes.String(), Serdes.String());

        assertThat(stream.filterNot((key, value) -> value.isBlank())).isNotNull();
        assertThat(stream.flatMap((key, value) -> java.util.List.of(KeyValue.pair(key, value)))).isNotNull();
        assertThat(stream.groupBy((key, value) -> key)).isNotNull();
        assertThat(stream.join(other, (key, left, right) -> left + right, windows, joined)).isNotNull();
        assertThat(stream.leftJoin(other, (key, left, right) -> left + right, windows, joined)).isNotNull();
        assertThat(stream.join(table, (key, left, right) -> left + right,
                org.apache.kafka.streams.kstream.Joined.with(Serdes.String(), Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(stream.leftJoin(table, (key, left, right) -> left + right,
                org.apache.kafka.streams.kstream.Joined.with(Serdes.String(), Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(stream.merge(other)).isNotNull();
        assertThat(stream.peek((key, value) -> assertThat(value).isNotBlank())).isNotNull();
        assertThat(stream.toTable(Named.as("batch-table"), materialized)).isNotNull();
        assertThat(builder.build().describe().toString()).contains("batch-dsl-stream");
    }

    @Test
    void shouldExerciseAllKTableForeignJoinConfigurationForms() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> left = builder.table("batch-table-left", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> right = builder.table("batch-table-right", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()));
        Materialized<String, String, KeyValueStore<Bytes, byte[]>> materialized =
                Materialized.with(Serdes.String(), Serdes.String());
        java.util.function.Function<String, String> foreignKey = value -> value;
        org.apache.kafka.streams.kstream.ValueJoiner<String, String, String> joiner = (a, b) -> a + b;

        assertThat(left.filterNot((key, value) -> value.isEmpty())).isNotNull();
        assertThat(left.mapValues((key, value) -> key + value)).isNotNull();
        assertThat(left.join(right, foreignKey, joiner)).isNotNull();
        assertThat(left.join(right, foreignKey, joiner, Named.as("foreign-named"))).isNotNull();
        assertThat(left.join(right, foreignKey, joiner, TableJoined.as("foreign-table"))).isNotNull();
        assertThat(left.join(right, foreignKey, joiner, materialized)).isNotNull();
        assertThat(left.join(right, foreignKey, joiner, Named.as("foreign-materialized"), materialized)).isNotNull();
        assertThat(left.join(right, foreignKey, joiner, TableJoined.as("foreign-both"), materialized)).isNotNull();
        assertThat(left.leftJoin(right, foreignKey, joiner)).isNotNull();
        assertThat(left.leftJoin(right, foreignKey, joiner, Named.as("left-named"))).isNotNull();
        assertThat(left.leftJoin(right, foreignKey, joiner, TableJoined.as("left-table"))).isNotNull();
        assertThat(left.leftJoin(right, foreignKey, joiner, materialized)).isNotNull();
        assertThat(left.leftJoin(right, foreignKey, joiner, Named.as("left-materialized"), materialized)).isNotNull();
        assertThat(left.leftJoin(right, foreignKey, joiner, TableJoined.as("left-both"), materialized)).isNotNull();
        assertThat(left.join(left, (a, b) -> a + b, Named.as("self-join"))).isNotNull();
        assertThat(left.leftJoin(left, (a, b) -> String.valueOf(a) + b, Named.as("self-left-join"))).isNotNull();
        assertThat(left.outerJoin(left, (a, b) -> String.valueOf(a) + b, Named.as("self-outer-join"))).isNotNull();
        assertThat(builder.build().describe().toString()).contains("batch-table-left");
    }

    @Test
    void shouldAggregateRegularSlidingSessionAndTimeCogroups() {
        StreamsBuilder builder = new StreamsBuilder();
        KGroupedStream<String, String> first = builder.stream("batch-cogroup-first", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()));
        KGroupedStream<String, String> second = builder.stream("batch-cogroup-second", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()));
        CogroupedKStream<String, String> cogroup = first.cogroup((key, value, aggregate) -> aggregate + value);
        cogroup = cogroup.cogroup(second, (key, value, aggregate) -> aggregate + value);
        Materialized<String, String, KeyValueStore<Bytes, byte[]>> materialized =
                Materialized.with(Serdes.String(), Serdes.String());

        assertThat(cogroup.aggregate(() -> "")).isNotNull();
        assertThat(cogroup.aggregate(() -> "", materialized)).isNotNull();
        assertThat(cogroup.aggregate(() -> "", Named.as("cogroup-named"))).isNotNull();
        assertThat(cogroup.aggregate(() -> "", Named.as("cogroup-both"), materialized)).isNotNull();
        assertThat(cogroup.windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(3))).aggregate(() -> "")).isNotNull();
        assertThat(cogroup.windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(3)))
                .aggregate(() -> "", Named.as("time-named"))).isNotNull();
        assertThat(cogroup.windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(3)))
                .aggregate(() -> "", Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(cogroup.windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2))).aggregate(() -> "")).isNotNull();
        assertThat(cogroup.windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)))
                .aggregate(() -> "", Named.as("sliding-named"))).isNotNull();
        assertThat(cogroup.windowedBy(SessionWindows.with(Duration.ofSeconds(2)))
                .aggregate(() -> "", (key, left, right) -> left + right)).isNotNull();
        assertThat(cogroup.windowedBy(SessionWindows.with(Duration.ofSeconds(2)))
                .aggregate(() -> "", (key, left, right) -> left + right, Named.as("session-named"),
                        Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(builder.build().describe().toString()).contains("batch-cogroup-first");
    }
}
