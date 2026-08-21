/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KGroupedTable;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises table joins, materialization, grouping, and windowed aggregations. */
public class KTableAndWindowApiCoverageTest {
    @Test
    void shouldJoinFilterTransformAndMaterializeTables() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> left = builder.table("api-table-left", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> right = builder.table("api-table-right", Consumed.with(Serdes.String(), Serdes.String()));
        Materialized<String, String, org.apache.kafka.streams.state.KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>> materialized =
                Materialized.with(Serdes.String(), Serdes.String());

        KTable<String, String> joined = left.join(right, (a, b) -> a + b, Named.as("table-join-named"), materialized);
        assertThat(left.join(right, (a, b) -> a + b, Named.as("join"))).isNotNull();
        assertThat(left.leftJoin(right, (a, b) -> String.valueOf(a) + b)).isNotNull();
        assertThat(left.leftJoin(right, (a, b) -> String.valueOf(a) + b, materialized)).isNotNull();
        assertThat(left.outerJoin(right, (a, b) -> String.valueOf(a) + b, materialized)).isNotNull();
        assertThat(left.filter((key, value) -> value != null, materialized)).isNotNull();
        assertThat(left.filterNot((key, value) -> value.isEmpty(), materialized)).isNotNull();
        assertThat(left.mapValues((org.apache.kafka.streams.kstream.ValueMapper<String, String>) String::toUpperCase, materialized)).isNotNull();
        assertThat(left.mapValues((key, value) -> key + value, materialized)).isNotNull();
        assertThat(left.toStream((key, value) -> key + "-stream", Named.as("table-to-stream"))).isNotNull();
        assertThat(left.suppress(Suppressed.untilTimeLimit(Duration.ofSeconds(1), Suppressed.BufferConfig.unbounded()))).isNotNull();

        KTable<String, String> foreign = builder.table("api-table-foreign", Consumed.with(Serdes.String(), Serdes.String()));
        assertThat(left.join(foreign, value -> value, (a, b) -> a + b)).isNotNull();
        assertThat(left.join(foreign, value -> value, (a, b) -> a + b, Named.as("foreign-named"))).isNotNull();
        assertThat(left.leftJoin(foreign, value -> value, (a, b) -> String.valueOf(a) + b)).isNotNull();
        assertThat(joined).isNotNull();
        assertThat(builder.build()).isNotNull();
    }

    @Test
    void shouldAggregateTablesAndWindowedStreams() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> table = builder.table("api-grouped-table", Consumed.with(Serdes.String(), Serdes.String()));
        KGroupedTable<String, String> grouped = table.groupBy((key, value) -> KeyValue.pair(key, value), Grouped.with(Serdes.String(), Serdes.String()));
        assertThat(grouped.reduce((oldValue, newValue) -> newValue, (oldValue, newValue) -> oldValue)).isNotNull();
        assertThat(grouped.aggregate(() -> "", (key, value, aggregate) -> aggregate + value, (key, value, aggregate) -> aggregate + value)).isNotNull();

        KStream<String, String> stream = builder.stream("api-window-input", Consumed.with(Serdes.String(), Serdes.String()));
        assertThat(stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10))).count()).isNotNull();
        assertThat(stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10))).count(Materialized.with(Serdes.String(), Serdes.Long()))).isNotNull();
        assertThat(stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10))).reduce((a, b) -> a + b)).isNotNull();
        assertThat(stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10))).aggregate(() -> "", (key, value, aggregate) -> aggregate + value)).isNotNull();

        assertThat(stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(5))).count()).isNotNull();
        assertThat(stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(5))).count(Materialized.with(Serdes.String(), Serdes.Long()))).isNotNull();
        assertThat(stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(5))).reduce((a, b) -> a + b)).isNotNull();
        assertThat(stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(5))).aggregate(() -> "", (key, value, aggregate) -> aggregate + value, (key, left, right) -> left + right)).isNotNull();

        assertThat(stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).count()).isNotNull();
        assertThat(stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).reduce((a, b) -> a + b)).isNotNull();
        assertThat(stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).aggregate(() -> "", (key, value, aggregate) -> aggregate + value)).isNotNull();
        assertThat(builder.build()).isNotNull();
    }
}
