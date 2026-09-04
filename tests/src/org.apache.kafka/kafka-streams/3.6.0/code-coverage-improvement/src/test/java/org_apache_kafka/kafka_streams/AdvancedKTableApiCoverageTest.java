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
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KGroupedTable;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.SessionWindowedKStream;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.TimeWindowedKStream;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises KTable overloads and the grouped/windowed table subsystems. */
public class AdvancedKTableApiCoverageTest {
    @Test
    void shouldConfigureAllTableTransformsAndJoins() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> left = builder.table("advanced-table-left", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> right = builder.table("advanced-table-right", Consumed.with(Serdes.String(), Serdes.String()));
        builder.addStateStore(org.apache.kafka.streams.state.Stores.keyValueStoreBuilder(
                org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore("table-store-a"), Serdes.String(), Serdes.String()));
        builder.addStateStore(org.apache.kafka.streams.state.Stores.keyValueStoreBuilder(
                org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore("table-store-b"), Serdes.String(), Serdes.String()));
        builder.addStateStore(org.apache.kafka.streams.state.Stores.keyValueStoreBuilder(
                org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore("table-store-c"), Serdes.String(), Serdes.String()));
        builder.addStateStore(org.apache.kafka.streams.state.Stores.keyValueStoreBuilder(
                org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore("table-store-d"), Serdes.String(), Serdes.String()));
        Materialized<String, String, org.apache.kafka.streams.state.KeyValueStore<Bytes, byte[]>> materialized =
                Materialized.with(Serdes.String(), Serdes.String());

        assertThat(left.filter((key, value) -> value != null)).isNotNull();
        assertThat(left.filter((key, value) -> value != null, Named.as("filter-named"))).isNotNull();
        assertThat(left.filterNot((key, value) -> value.isEmpty(), Named.as("filter-not-named"))).isNotNull();
        assertThat(left.filter((key, value) -> true, Named.as("filter-materialized"), materialized)).isNotNull();
        assertThat(left.filterNot((key, value) -> false, Named.as("filter-not-materialized"), materialized)).isNotNull();
        assertThat(left.mapValues(value -> value.length())).isNotNull();
        assertThat(left.mapValues(value -> value.length(), Named.as("map-values-named"))).isNotNull();
        assertThat(left.mapValues((key, value) -> key + value, Named.as("map-values-keyed"))).isNotNull();
        assertThat(left.mapValues((key, value) -> key + value, Named.as("map-values-keyed-materialized"), materialized)).isNotNull();

        ValueTransformerWithKeySupplier<String, String, String> transformer = () -> new ValueTransformerWithKey<>() {
            @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
            @Override public String transform(String key, String value) {
                return key + value;
            }
            @Override public void close() { }
        };
        assertThat(left.transformValues(transformer, "table-store-a")).isNotNull();
        assertThat(left.transformValues(transformer, Named.as("table-transform-named"), "table-store-b")).isNotNull();
        assertThat(left.transformValues(transformer, materialized, "table-store-c")).isNotNull();
        assertThat(left.transformValues(transformer, materialized, Named.as("table-transform-configured"), "table-store-d")).isNotNull();
        assertThat(left.toStream()).isNotNull();
        assertThat(left.toStream(Named.as("to-stream-named"))).isNotNull();
        assertThat(left.toStream((key, value) -> key + "-mapped", Named.as("to-stream-mapped"))).isNotNull();

        assertThat(left.join(right, (a, b) -> a + b)).isNotNull();
        assertThat(left.join(right, (a, b) -> a + b, Named.as("join-named"))).isNotNull();
        assertThat(left.join(right, (a, b) -> a + b, materialized)).isNotNull();
        assertThat(left.join(right, (a, b) -> a + b, Named.as("join-configured"), materialized)).isNotNull();
        assertThat(left.leftJoin(right, (a, b) -> String.valueOf(a) + b, Named.as("left-named"))).isNotNull();
        assertThat(left.leftJoin(right, (a, b) -> String.valueOf(a) + b, Named.as("left-configured"), materialized)).isNotNull();
        assertThat(left.outerJoin(right, (a, b) -> String.valueOf(a) + b)).isNotNull();
        assertThat(left.outerJoin(right, (a, b) -> String.valueOf(a) + b, Named.as("outer-named"))).isNotNull();
        assertThat(left.outerJoin(right, (a, b) -> String.valueOf(a) + b, Named.as("outer-configured"), materialized)).isNotNull();

        assertThat(left.join(right, value -> value, (a, b) -> a + b, Named.as("foreign-named"), materialized)).isNotNull();
        assertThat(left.join(right, value -> value, (a, b) -> a + b,
                org.apache.kafka.streams.kstream.TableJoined.as("foreign-table-joined"), materialized)).isNotNull();
        assertThat(left.leftJoin(right, value -> value, (a, b) -> String.valueOf(a) + b,
                org.apache.kafka.streams.kstream.TableJoined.as("foreign-left"), materialized)).isNotNull();
        assertThat(left.queryableStoreName()).isNull();
        assertThat(builder.build().describe().toString()).contains("advanced-table-left");
    }

    @Test
    void shouldAggregateGroupedAndWindowedStreamsWithOptions() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> table = builder.table("advanced-group-table", Consumed.with(Serdes.String(), Serdes.String()));
        KGroupedTable<String, String> groupedTable = table.groupBy(
                (key, value) -> KeyValue.pair(key, value), Grouped.with(Serdes.String(), Serdes.String()));
        Materialized<String, Long, org.apache.kafka.streams.state.KeyValueStore<Bytes, byte[]>> counts =
                Materialized.with(Serdes.String(), Serdes.Long());
        assertThat(groupedTable.count()).isNotNull();
        assertThat(groupedTable.count(Named.as("table-count-named"))).isNotNull();
        assertThat(groupedTable.count(Named.as("table-count-configured"), counts)).isNotNull();
        assertThat(groupedTable.aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                (key, value, aggregate) -> aggregate + value, Named.as("table-aggregate-named"))).isNotNull();

        KStream<String, String> source = builder.stream("advanced-window-source",
                Consumed.with(Serdes.String(), Serdes.String()));
        KGroupedStream<String, String> grouped = source.groupByKey();
        TimeWindowedKStream<String, String> time = grouped.windowedBy(TimeWindows.of(Duration.ofSeconds(5)));
        assertThat(time.count(Named.as("time-count"))).isNotNull();
        assertThat(time.count(Named.as("time-count-materialized"),
                Materialized.with(Serdes.String(), Serdes.Long()))).isNotNull();
        assertThat(time.reduce((a, b) -> a + b, Named.as("time-reduce"))).isNotNull();
        assertThat(time.reduce((a, b) -> a + b, Named.as("time-reduce-materialized"),
                Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(time.aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                Named.as("time-aggregate-named"))).isNotNull();
        assertThat(time.emitStrategy(org.apache.kafka.streams.kstream.EmitStrategy.onWindowClose())).isNotNull();

        SessionWindowedKStream<String, String> session = grouped.windowedBy(SessionWindows.with(Duration.ofSeconds(3)));
        assertThat(session.count(Named.as("session-count"))).isNotNull();
        assertThat(session.count(Named.as("session-count-materialized"),
                Materialized.with(Serdes.String(), Serdes.Long()))).isNotNull();
        assertThat(session.reduce((a, b) -> a + b, Named.as("session-reduce"),
                Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(session.aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                (key, aggregate, merged) -> aggregate + merged, Named.as("session-aggregate"),
                Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(session.emitStrategy(org.apache.kafka.streams.kstream.EmitStrategy.onWindowClose())).isNotNull();

        assertThat(grouped.windowedBy(SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .count(Named.as("sliding-count"))).isNotNull();
        assertThat(builder.build().describe().toString()).contains("advanced-window-source");
    }
}
