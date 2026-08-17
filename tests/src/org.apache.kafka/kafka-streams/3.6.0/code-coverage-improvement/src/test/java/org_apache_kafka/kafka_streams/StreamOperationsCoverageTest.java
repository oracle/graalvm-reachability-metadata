/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamOperationsCoverageTest {
    @Test
    void mappingFilteringPeekingMergingAndBranchingExecuteInOneTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> first = builder.stream("first", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> second = builder.stream("second", Consumed.with(Serdes.String(), Serdes.String()));
        List<String> observed = new ArrayList<>();

        KStream<String, String> mapped = first
                .filterNot((key, value) -> value.startsWith("skip"))
                .filterNot((key, value) -> value.isBlank(), Named.as("non-blank"))
                .map((key, value) -> KeyValue.pair(key + "-mapped", value), Named.as("map-named"))
                .flatMap((key, value) -> List.of(KeyValue.pair(key, value), KeyValue.pair(key, value + "!")))
                .flatMap((key, value) -> List.of(KeyValue.pair(key, value)), Named.as("flat-map-named"))
                .mapValues((org.apache.kafka.streams.kstream.ValueMapper<String, String>) String::toUpperCase)
                .mapValues((key, value) -> key + ":" + value)
                .flatMapValues(value -> List.of(value, value + "?"))
                .flatMapValues((key, value) -> List.of(value + "@" + key))
                .peek((key, value) -> observed.add(value))
                .peek((key, value) -> observed.add("named:" + value), Named.as("peek-named"));

        KStream<String, String> merged = mapped.merge(second).merge(second, Named.as("merge-named"));
        Map<String, KStream<String, String>> branches = merged.split()
                .branch((key, value) -> value.contains("A"))
                .defaultBranch(Branched.as("other"));
        branches.values().forEach(stream -> stream.to("result", Produced.with(Serdes.String(), Serdes.String())));
        Topology topology = builder.build();

        assertThat(observed).isEmpty();
        assertThat(branches).hasSize(2).anySatisfy((name, stream) -> assertThat(name).endsWith("other"));
        assertThat(topology.describe().toString())
                .contains("non-blank", "map-named", "flat-map-named", "peek-named", "result");
    }

    @Test
    void streamTableGlobalTableAndStreamJoinsBuildEveryJoinFamily() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> left = builder.stream("join-left", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> right = builder.stream("join-right", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table("join-table", Materialized.as("join-table-store"));
        GlobalKTable<String, String> global = builder.globalTable("join-global", Materialized.as("join-global-store"));
        JoinWindows windows = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5));

        List<KStream<String, String>> joins = List.of(
                left.leftJoin(right, (a, b) -> a + b, windows),
                left.leftJoin(right, (key, a, b) -> key + a + b, windows),
                left.leftJoin(right, (key, a, b) -> key + a + b, windows,
                        StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String())),
                left.outerJoin(right, (a, b) -> a + b, windows),
                left.outerJoin(right, (key, a, b) -> key + a + b, windows),
                left.outerJoin(right, (key, a, b) -> key + a + b, windows,
                        StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String())),
                left.join(right, (a, b) -> a + b, windows,
                        StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String())),
                left.join(table, (a, b) -> a + b, Joined.with(Serdes.String(), Serdes.String(), Serdes.String())),
                left.leftJoin(table, (a, b) -> a + b, Joined.with(Serdes.String(), Serdes.String(), Serdes.String())),
                left.join(global, (key, value) -> key, (a, b) -> a + b, Named.as("global-inner")),
                left.leftJoin(global, (key, value) -> key, (a, b) -> a + b, Named.as("global-left")));

        assertThat(joins).hasSize(11).doesNotContainNull();
        assertThat(builder.build().describe().toString()).contains("global-inner", "global-left", "join-table-store");
    }

    @Test
    void throughAndPrintProduceInspectableTopologyNodes() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> source = builder.stream("through-input",
                Consumed.with(Serdes.String(), Serdes.String()));
        source.through("through-default");
        source.through("through-serde", Produced.with(Serdes.String(), Serdes.String()));
        source.print(org.apache.kafka.streams.kstream.Printed.<String, String>toSysOut()
                .withLabel("coverage-print").withName("print-node")
                .withKeyValueMapper((key, value) -> key + "=" + value));

        assertThat(builder.build().describe().toString())
                .contains("through-default", "through-serde", "print-node");
    }
}
