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
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Builds public Kafka Streams topologies for processor, join, and window paths. */
public class DeepTopologyConstructionCoverageTest {
    @Test
    void shouldBuildWindowTransformAndJoinProcessorsThroughPublicDsl() {
        StreamsBuilder builder = new StreamsBuilder();
        Consumed<String, String> consumed = Consumed.with(Serdes.String(), Serdes.String());
        Produced<String, String> produced = Produced.with(Serdes.String(), Serdes.String());
        KStream<String, String> events = builder.stream("deep-events", consumed);
        KStream<String, String> other = builder.stream("deep-other", consumed);
        KTable<String, String> lookup = builder.table("deep-lookup", consumed);

        events.flatTransformValues(() -> new org.apache.kafka.streams.kstream.ValueTransformerWithKey<String, String, Iterable<String>>() {
            @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
            @Override public Iterable<String> transform(String key, String value) {
                return java.util.List.of(value, key + value);
            }
            @Override public void close() { }
        }).to("deep-flat-output", produced);
        events.join(other, (key, left, right) -> left + right,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5)),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()))
                .to("deep-stream-join-output", produced);
        events.join(lookup, (key, left, right) -> left + right).to("deep-table-join-output", produced);

        KGroupedStream<String, String> grouped = events.groupByKey(Grouped.with(Serdes.String(), Serdes.String()));
        grouped.windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(4)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value)
                .toStream()
                .map((window, value) -> KeyValue.pair(window.key(), value))
                .to("deep-sliding-output", produced);
        grouped.windowedBy(SessionWindows.with(Duration.ofSeconds(4)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                        (key, left, right) -> left + right)
                .toStream()
                .map((window, value) -> KeyValue.pair(window.key(), value))
                .to("deep-session-output", produced);

        assertThat(builder.build().describe().toString()).contains("deep-events", "deep-sliding-output");
    }

    @Test
    void shouldBuildPatternSourcesAndOptimizedRepartitionThroughPublicBuilder() {
        StreamsBuilder builder = new StreamsBuilder();
        Consumed<String, String> consumed = Consumed.with(Serdes.String(), Serdes.String());
        KStream<String, String> keyed = builder.stream("optimization-input", consumed)
                .selectKey((key, value) -> value)
                .filter((key, value) -> value != null);
        keyed.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .count()
                .toStream()
                .to("optimization-output", Produced.with(Serdes.String(), Serdes.Long()));
        builder.stream(Pattern.compile("pattern-input-.*"), consumed).to("pattern-output", Produced.with(
                Serdes.String(), Serdes.String()));
        Properties properties = new Properties();
        properties.put(org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG, "deep-optimization");
        properties.put(org.apache.kafka.streams.StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(org.apache.kafka.streams.StreamsConfig.TOPOLOGY_OPTIMIZATION_CONFIG, "all");
        assertThat(builder.build(properties).describe().toString()).contains("optimization-output", "pattern-output");
    }

    @Test
    void shouldBuildAllPublicTableJoinShapesAndRepartitionedForeignJoin() {
        StreamsBuilder builder = new StreamsBuilder();
        Consumed<String, String> consumed = Consumed.with(Serdes.String(), Serdes.String());
        Produced<String, String> produced = Produced.with(Serdes.String(), Serdes.String());
        KTable<String, String> left = builder.table("deep-left", consumed);
        KTable<String, String> right = builder.table("deep-right", consumed);
        KTable<String, String> foreign = builder.table("deep-foreign", consumed);

        left.join(right, (a, b) -> a + b).toStream().to("deep-inner-output", produced);
        left.leftJoin(right, (a, b) -> String.valueOf(a) + b).toStream().to("deep-left-output", produced);
        left.outerJoin(right, (a, b) -> String.valueOf(a) + b).toStream().to("deep-outer-output", produced);
        left.join(foreign, value -> value, (a, b) -> a + b,
                TableJoined.as("deep-foreign-join")).toStream().to("deep-foreign-output", produced);
        left.leftJoin(foreign, value -> value, (a, b) -> String.valueOf(a) + b,
                TableJoined.as("deep-foreign-left")).toStream().to("deep-foreign-left-output", produced);
        left.filter((key, value) -> value != null)
                .mapValues((key, value) -> key + value)
                .toStream().to("deep-table-transform-output", produced);

        assertThat(builder.build().describe().toString()).contains("deep-left", "deep-foreign-output");
    }
}
