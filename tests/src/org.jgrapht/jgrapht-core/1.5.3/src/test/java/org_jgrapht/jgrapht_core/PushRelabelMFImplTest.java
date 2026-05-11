/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.flow.PushRelabelMFImpl;
import org.jgrapht.alg.interfaces.MaximumFlowAlgorithm.MaximumFlow;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PushRelabelMFImplTest {
    @Test
    void computesMaximumFlowForDirectedWeightedNetwork() {
        Graph<String, DefaultWeightedEdge> graph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultWeightedEdge sourceToA = addEdge(graph, "source", "a", 3.0d);
        DefaultWeightedEdge sourceToB = addEdge(graph, "source", "b", 2.0d);
        addEdge(graph, "a", "b", 1.0d);
        addEdge(graph, "a", "sink", 2.0d);
        addEdge(graph, "b", "sink", 3.0d);

        PushRelabelMFImpl<String, DefaultWeightedEdge> algorithm =
            new PushRelabelMFImpl<>(graph);

        MaximumFlow<DefaultWeightedEdge> maximumFlow = algorithm.getMaximumFlow("source", "sink");

        assertThat(maximumFlow.getValue()).isEqualTo(5.0d);
        assertThat(maximumFlow.getFlowMap()).hasSize(graph.edgeSet().size());
        assertThat(maximumFlow.getFlow(sourceToA) + maximumFlow.getFlow(sourceToB)).isEqualTo(5.0d);
    }

    private static DefaultWeightedEdge addEdge(
        Graph<String, DefaultWeightedEdge> graph, String source, String target, double capacity)
    {
        graph.addVertex(source);
        graph.addVertex(target);
        DefaultWeightedEdge edge = graph.addEdge(source, target);
        graph.setEdgeWeight(edge, capacity);
        return edge;
    }
}
