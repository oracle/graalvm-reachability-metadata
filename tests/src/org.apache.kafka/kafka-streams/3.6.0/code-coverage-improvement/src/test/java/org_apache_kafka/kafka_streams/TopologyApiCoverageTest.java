/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.processor.internals.ProcessorNode;
import org.apache.kafka.streams.processor.internals.ProcessorTopology;
import org.apache.kafka.streams.processor.internals.SourceNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyApiCoverageTest {
    @Test
    void processorTopologyDescriptionIncludesChildrenAndTopics() {
        SourceNode<Object, Object> source = new SourceNode<>("source", null, null);
        ProcessorNode<Object, Object, Object, Object> child = new ProcessorNode<>("child");
        source.addChild(child);
        ProcessorTopology topology = new ProcessorTopology(
                List.of(source, child), Map.of("input-topic", source), Map.of(), List.of(), List.of(),
                Map.of("store", "store-changelog"), Set.of("repartition-topic"));

        assertThat(topology.toString("  ")).contains("source", "child", "input-topic");
        assertThat(topology.toString()).contains("ProcessorTopology");
    }

    @Test
    void sourceAndSinkOverloadsDescribeTheirConfiguredBehavior() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "topology-api");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        Topology topology = new Topology(new TopologyConfig(new StreamsConfig(properties)));
        FailOnInvalidTimestamp extractor = new FailOnInvalidTimestamp();

        topology.addSource("source-one", Serdes.String().deserializer(), Serdes.String().deserializer(),
                        Pattern.compile("input-one"))
                .addSource(Topology.AutoOffsetReset.EARLIEST, "source-two", "input-two")
                .addSource(Topology.AutoOffsetReset.LATEST, "source-three", Pattern.compile("input-three"))
                .addSource(Topology.AutoOffsetReset.EARLIEST, "source-four", Serdes.String().deserializer(),
                        Serdes.String().deserializer(), "input-four")
                .addSource(Topology.AutoOffsetReset.LATEST, "source-five", Serdes.String().deserializer(),
                        Serdes.String().deserializer(), Pattern.compile("input-five"))
                .addSource(Topology.AutoOffsetReset.EARLIEST, "source-six", extractor,
                        Serdes.String().deserializer(), Serdes.String().deserializer(), "input-six")
                .addSource(Topology.AutoOffsetReset.LATEST, "source-seven", extractor,
                        Serdes.String().deserializer(), Serdes.String().deserializer(), Pattern.compile("input-seven"))
                .addSource(Topology.AutoOffsetReset.EARLIEST, extractor, "source-eight", "input-eight")
                .addSource(Topology.AutoOffsetReset.LATEST, extractor, "source-nine", Pattern.compile("input-nine"))
                .addSource(extractor, "source-ten", "input-ten")
                .addSource(extractor, "source-eleven", Pattern.compile("input-eleven"));

        topology.addSink("sink-one", "output-one", Serdes.String().serializer(), Serdes.String().serializer(),
                        (topic, key, value, partitions) -> 0, "source-one")
                .addSink("sink-two", "output-two", (topic, key, value, partitions) -> 0, "source-two")
                .addSink("sink-three", (key, value, context) -> "output-three", Serdes.String().serializer(),
                        Serdes.String().serializer(), "source-three")
                .addSink("sink-four", (key, value, context) -> "output-four", Serdes.String().serializer(),
                        Serdes.String().serializer(), (topic, key, value, partitions) -> 0, "source-four")
                .addSink("sink-five", (key, value, context) -> "output-five",
                        (topic, key, value, partitions) -> 0, "source-five");

        String description = topology.describe().toString();
        assertThat(description).contains("source-one", "source-eleven", "sink-one", "sink-five");
        assertThat(Topology.AutoOffsetReset.values()).contains(Topology.AutoOffsetReset.EARLIEST);
        assertThat(Topology.AutoOffsetReset.valueOf("LATEST")).isEqualTo(Topology.AutoOffsetReset.LATEST);
    }
}
