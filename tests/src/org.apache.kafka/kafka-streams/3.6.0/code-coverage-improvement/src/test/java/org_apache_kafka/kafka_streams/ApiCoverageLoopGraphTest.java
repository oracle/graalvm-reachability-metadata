/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.internals.ConsumedInternal;
import org.apache.kafka.streams.kstream.internals.InternalStreamsBuilder;
import org.apache.kafka.streams.kstream.internals.graph.GraphNode;
import org.apache.kafka.streams.kstream.internals.graph.OptimizableRepartitionNode;
import org.apache.kafka.streams.kstream.internals.graph.ProcessorParameters;
import org.apache.kafka.streams.kstream.internals.graph.StreamSourceNode;
import org.apache.kafka.streams.kstream.internals.graph.StreamStreamJoinNode;
import org.apache.kafka.streams.kstream.internals.graph.TableProcessorNode;
import org.apache.kafka.streams.kstream.internals.graph.TableSourceNode;
import org.apache.kafka.streams.processor.internals.InternalTopologyBuilder;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorSupplier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the graph objects produced while public stream builders assemble a topology. */
public class ApiCoverageLoopGraphTest {
    @Test
    void shouldManageGraphParentsChildrenAndSourceSerdes() {
        InternalStreamsBuilder streamsBuilder = new InternalStreamsBuilder(new InternalTopologyBuilder());
        assertThat(streamsBuilder.root()).isNotNull();
        streamsBuilder.buildAndOptimizeTopology();
        assertThat(streamsBuilder.root().children()).isEmpty();

        ConsumedInternal<String, String> consumed = new ConsumedInternal<>(
                Consumed.with(Serdes.String(), Serdes.String()));
        StreamSourceNode<String, String> source = new StreamSourceNode<>(
                "source", List.of("topic"), consumed);
        StreamSourceNode<String, String> other = new StreamSourceNode<>(
                "other", List.of("other-topic"), consumed);
        assertThat(source.keySerde()).isNotNull();
        assertThat(source.valueSerde()).isNotNull();
        source.merge(other);
        assertThat(source.toString()).contains("source");

        GraphNode child = new EmptyGraphNode("child");
        source.addChild(child);
        assertThat(source.children()).contains(child);
        assertThat(source.removeChild(child)).isTrue();
        source.addChild(child);
        source.clearChildren();
        assertThat(source.children()).isEmpty();
    }

    @Test
    void shouldBuildProcessorAndRepartitionGraphNodes() {
        FixedKeyProcessorSupplier<String, String, String> fixedSupplier = () -> new FixedKeyProcessor<>() {
            @Override public void init(org.apache.kafka.streams.processor.api.FixedKeyProcessorContext<String, String> context) { }
            @Override public void process(org.apache.kafka.streams.processor.api.FixedKeyRecord<String, String> record) { }
        };
        ProcessorParameters<String, String, String, String> parameters = new ProcessorParameters<>(fixedSupplier, "fixed");
        assertThat(parameters.fixedKeyProcessorSupplier()).isSameAs(fixedSupplier);
        assertThat(parameters.processorName()).isEqualTo("fixed");

        TableProcessorNode<String, String> tableProcessor = new TableProcessorNode<>("table-processor", parameters, null);
        assertThat(tableProcessor.processorParameters()).isSameAs(parameters);
        assertThat(tableProcessor.toString()).contains("table-processor");

        TableSourceNode<String, String> tableSource = TableSourceNode.<String, String>tableSourceNodeBuilder()
                .withNodeName("table-source")
                .withSourceName("source")
                .withTopic("topic")
                .withConsumedInternal(new ConsumedInternal<>(Consumed.with(Serdes.String(), Serdes.String())))
                .withProcessorParameters(parameters)
                .isGlobalKTable(false)
                .build();
        tableSource.reuseSourceTopicForChangeLog(true);
        assertThat(tableSource.keySerde()).isNotNull();
        assertThat(tableSource.valueSerde()).isNotNull();

        OptimizableRepartitionNode<String, String> repartition =
                OptimizableRepartitionNode.<String, String>optimizableRepartitionNodeBuilder()
                        .withNodeName("repartition-node")
                        .withProcessorParameters(parameters)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.String())
                        .withSinkName("sink")
                        .withSourceName("source")
                        .withRepartitionTopic("repartition-topic")
                        .build();
        assertThat(repartition.keySerde()).isNotNull();
        assertThat(repartition.valueSerde()).isNotNull();
        assertThat(repartition.repartitionTopic()).isEqualTo("repartition-topic");

        StreamStreamJoinNode.StreamStreamJoinNodeBuilder<String, String, String, String> joinBuilder =
                StreamStreamJoinNode.streamStreamJoinNodeBuilder();
        assertThat(joinBuilder.withNodeName("join-node")
                .withValueJoiner((key, left, right) -> left + right)
                .withJoined(Joined.with(Serdes.String(), Serdes.String(), Serdes.String()))).isNotNull();
    }

    private static final class EmptyGraphNode extends GraphNode {
        private EmptyGraphNode(String name) {
            super(name);
        }
        @Override public void writeToTopology(InternalTopologyBuilder builder) { }
    }
}
