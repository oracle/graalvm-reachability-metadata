/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm.SpanningTree;
import org.jgrapht.alg.spanning.PrimMinimumSpanningTree;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrimMinimumSpanningTreeTest {
    @Test
    void computesMinimumSpanningTreeForWeightedGraph() {
        Graph<String, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultWeightedEdge ab = addWeightedEdge(graph, "a", "b", 1.0d);
        DefaultWeightedEdge bc = addWeightedEdge(graph, "b", "c", 2.0d);
        DefaultWeightedEdge cd = addWeightedEdge(graph, "c", "d", 3.0d);
        addWeightedEdge(graph, "a", "c", 4.0d);
        addWeightedEdge(graph, "a", "d", 10.0d);

        PrimMinimumSpanningTree<String, DefaultWeightedEdge> algorithm =
            new PrimMinimumSpanningTree<>(graph);

        SpanningTree<DefaultWeightedEdge> spanningTree = algorithm.getSpanningTree();

        assertThat(spanningTree.getWeight()).isEqualTo(6.0d);
        assertThat(spanningTree.getEdges()).containsExactlyInAnyOrder(ab, bc, cd);
    }

    private static DefaultWeightedEdge addWeightedEdge(
        Graph<String, DefaultWeightedEdge> graph, String source, String target, double weight) {
        graph.addVertex(source);
        graph.addVertex(target);
        DefaultWeightedEdge edge = graph.addEdge(source, target);
        graph.setEdgeWeight(edge, weight);
        return edge;
    }
}
