/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorExecutionCoverageTest {
    @Test
    void publicTopologyConstructionRoutesEveryStreamProcessorFamily() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> left = builder.stream("left", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> right = builder.stream("right", Consumed.with(Serdes.String(), Serdes.String()));

        left.filter((key, value) -> value != null)
                .map((key, value) -> org.apache.kafka.streams.KeyValue.pair(key, value.toUpperCase()))
                .flatMap((key, value) -> List.of(org.apache.kafka.streams.KeyValue.pair(key, value)))
                .flatMapValues(value -> List.of(value, value + "!"))
                .peek((key, value) -> { })
                .split().branch((key, value) -> value.endsWith("!"))
                .defaultBranch().values().forEach(stream -> stream.to("mapped"));

        JoinWindows windows = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5));
        left.join(right, (a, b) -> a + b, windows,
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String())).to("inner");
        left.leftJoin(right, (a, b) -> a + b, windows).to("left-join");
        left.outerJoin(right, (a, b) -> a + b, windows).to("outer");

        Topology topology = builder.build();
        assertThat(topology.describe().toString())
                .contains("mapped", "inner", "left-join", "outer");
    }

    @Test
    void optimizedTopologyMergesCompatiblePublicRepartitionOperations() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> keyed = builder
                .stream("optimized-input", Consumed.with(Serdes.String(), Serdes.String()))
                .selectKey((key, value) -> value);
        keyed.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .count(Materialized.as("optimized-counts")).toStream().to("optimized-count-output");
        keyed.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                        Materialized.as("optimized-aggregates"))
                .toStream().to("optimized-aggregate-output");

        Properties optimization = new Properties();
        optimization.put(StreamsConfig.TOPOLOGY_OPTIMIZATION_CONFIG, StreamsConfig.OPTIMIZE);
        Topology topology = builder.build(optimization);

        assertThat(topology.describe().toString())
                .contains("optimized-counts", "optimized-aggregates", "optimized-input");
    }

    @Test
    void publicTopologyConstructionRoutesTableAndAggregateProcessorFamilies() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> first = builder.table("first-table", Materialized.as("first-store"));
        KTable<String, String> second = builder.table("second-table", Materialized.as("second-store"));
        first.filter((key, value) -> value.startsWith("a"))
                .mapValues((org.apache.kafka.streams.kstream.ValueMapper<String, String>) String::toUpperCase)
                .join(second, (a, b) -> a + b).toStream().to("table-inner");
        first.leftJoin(second, (a, b) -> a + b).toStream().to("table-left");
        first.outerJoin(second, (a, b) -> String.valueOf(a) + b).toStream().to("table-outer");

        KStream<String, String> events = builder.stream("events", Consumed.with(Serdes.String(), Serdes.String()));
        events.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                        Materialized.with(Serdes.String(), Serdes.String())).toStream().to("aggregate");
        events.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
                .count(Materialized.as("time-window-store")).toStream().to("time-count");
        events.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(10)))
                .count(Materialized.as("session-store")).toStream().to("session-count");

        assertThat(builder.build().describe().toString())
                .contains("table-inner", "table-left", "table-outer", "aggregate",
                        "time-window-store", "session-store");
    }
}
