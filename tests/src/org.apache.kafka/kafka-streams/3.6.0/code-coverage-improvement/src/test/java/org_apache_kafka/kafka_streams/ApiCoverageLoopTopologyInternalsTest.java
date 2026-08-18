/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.processor.internals.InternalTopologyBuilder;
import org.apache.kafka.streams.processor.internals.ProcessorNode;
import org.apache.kafka.streams.processor.internals.ProcessorTopology;
import org.apache.kafka.streams.processor.internals.QuickUnion;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Builds low-level topology descriptions and verifies their public graph contracts. */
public class ApiCoverageLoopTopologyInternalsTest {
    @Test
    void shouldDescribeSourcesProcessorsSinksAndStores() {
        InternalTopologyBuilder builder = new InternalTopologyBuilder();
        builder.setApplicationId("coverage-topology");
        builder.addSource(null, "source", null, Serdes.String().deserializer(), Serdes.String().deserializer(), "orders");
        ProcessorSupplier<String, String, String, String> supplier = () -> new org.apache.kafka.streams.processor.api.Processor<>() {
            @Override
            public void process(Record<String, String> record) {
                // This processor is intentionally a pass-through topology component.
            }
        };
        builder.addProcessor("processor", supplier, "source");
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore("store"), Serdes.String(), Serdes.String()), "processor");
        builder.addSink("sink", "output", Serdes.String().serializer(), Serdes.String().serializer(), null, "processor");

        assertThat(builder.allStateStoreNames()).contains("store");
        assertThat(builder.stateStores()).containsKey("store");
        assertThat(builder.sourceTopicsForStore("store")).contains("orders");
        assertThat(builder.decoratePseudoTopic("output")).contains("output");
        assertThat(builder.isStoreVersioned("store")).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> builder.getHistoryRetention("store"))
                .isInstanceOf(IllegalStateException.class);
        builder.maybeUpdateCopartitionSourceGroups("source", "processor");

        ProcessorTopology topology = builder.buildSubtopology(0);
        assertThat(topology.sourceTopics()).contains("orders");
        assertThat(topology.processorConnectedStateStores("processor")).contains("store");
        assertThat(topology.hasPersistentLocalStore()).isFalse();
        assertThat(topology.hasPersistentGlobalStore()).isFalse();
        assertThat(topology.toString()).contains("processor");
        assertThat(topology.toString("  ")).contains("processor");
        topology.updateSourceTopics(Map.of("source", List.of("orders", "returns")));
        assertThat(topology.sourceTopics()).contains("returns");
    }

    @Test
    void shouldConnectDescriptionNodesAndPreserveValueSemantics() {
        InternalTopologyBuilder.Source source = new InternalTopologyBuilder.Source(
                "source", Set.of("orders"), null);
        InternalTopologyBuilder.Source patternSource = new InternalTopologyBuilder.Source(
                "pattern-source", null, Pattern.compile("returns-.*"));
        InternalTopologyBuilder.Processor processor = new InternalTopologyBuilder.Processor("processor", Set.of("store"));
        InternalTopologyBuilder.Sink<String, String> sink = new InternalTopologyBuilder.Sink<>("sink", "output");
        source.addSuccessor(processor);
        processor.addPredecessor(source);
        processor.addSuccessor(sink);
        sink.addPredecessor(processor);

        assertThat(source.topicSet()).containsExactly("orders");
        assertThat(patternSource.topicPattern().matcher("returns-eu").matches()).isTrue();
        assertThat(source.successors()).containsExactly(processor);
        assertThat(processor.stores()).containsExactly("store");
        assertThat(sink.topicNameExtractor()).isNull();
        InternalTopologyBuilder.Sink<String, String> dynamicSink = new InternalTopologyBuilder.Sink<>(
                "dynamic", (key, value, context) -> "dynamic-" + key);
        assertThat(dynamicSink.topicNameExtractor().extract("k", "v", null)).isEqualTo("dynamic-k");
        assertThat(sink.predecessors()).containsExactly(processor);

        InternalTopologyBuilder.TopologyDescription description = new InternalTopologyBuilder.TopologyDescription();
        assertThat(description.subtopologies()).isEmpty();
        assertThat(description.globalStores()).isEmpty();
        assertThat(description).isEqualTo(new InternalTopologyBuilder.TopologyDescription());
        assertThat(description.hashCode()).isEqualTo(new InternalTopologyBuilder.TopologyDescription().hashCode());
        assertThat(description.toString()).isNotNull();
    }

    @Test
    void shouldUseQuickUnionAndBuildPublicDslTopology() {
        QuickUnion<String> union = new QuickUnion<>();
        union.add("orders");
        union.add("returns");
        assertThat(union.exists("orders")).isTrue();
        assertThat(union.exists("missing")).isFalse();
        assertThat(union.root("orders")).isEqualTo("orders");
        assertThat(union.root("returns")).isEqualTo("returns");

        org.apache.kafka.streams.StreamsBuilder streamsBuilder = new org.apache.kafka.streams.StreamsBuilder();
        streamsBuilder.stream("orders", Consumed.with(Serdes.String(), Serdes.String())).to("output");
        Topology topology = streamsBuilder.build();
        assertThat(topology.describe().toString()).contains("orders", "output");
        assertThat(topology.describe().hashCode()).isEqualTo(topology.describe().hashCode());
        assertThat(new TopologyConfig(new StreamsConfig(Map.of(
                StreamsConfig.APPLICATION_ID_CONFIG, "topology-config",
                StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"))).toString()).isNotNull();
    }

    @Test
    void shouldRenderAndCloseProcessorNodes() {
        ProcessorNode<String, String, String, String> node = new ProcessorNode<>("node");
        ProcessorNode<String, String, String, String> child = new ProcessorNode<>("child");
        node.addChild(child);
        assertThat(node.children()).containsExactly(child);
        assertThat(node.isTerminalNode()).isFalse();
        assertThat(child.isTerminalNode()).isTrue();
        assertThat(node.toString()).contains("node");
        assertThat(node.toString("--")).contains("node");
        node.punctuate(5L, timestamp -> { });
        org.assertj.core.api.Assertions.assertThatThrownBy(node::close)
                .isInstanceOf(IllegalStateException.class);
        assertThat(node.toString()).contains("node");
    }
}
