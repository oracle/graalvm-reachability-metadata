/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths;
import org.jgrapht.alg.shortestpath.BellmanFordShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BellmanFordShortestPathTest {
    @Test
    void computesSingleSourcePathsWithNegativeEdgeWeights() {
        Graph<String, DefaultWeightedEdge> graph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        addWeightedEdge(graph, "a", "b", 4.0d);
        DefaultWeightedEdge ac = addWeightedEdge(graph, "a", "c", 2.0d);
        DefaultWeightedEdge cb = addWeightedEdge(graph, "c", "b", -1.0d);
        DefaultWeightedEdge bd = addWeightedEdge(graph, "b", "d", 2.0d);
        addWeightedEdge(graph, "c", "d", 5.0d);
        graph.addVertex("e");

        BellmanFordShortestPath<String, DefaultWeightedEdge> algorithm =
            new BellmanFordShortestPath<>(graph);

        SingleSourcePaths<String, DefaultWeightedEdge> paths = algorithm.getPaths("a");
        GraphPath<String, DefaultWeightedEdge> pathToD = paths.getPath("d");

        assertThat(paths.getSourceVertex()).isEqualTo("a");
        assertThat(paths.getWeight("d")).isEqualTo(3.0d);
        assertThat(pathToD.getVertexList()).containsExactly("a", "c", "b", "d");
        assertThat(pathToD.getEdgeList()).containsExactly(ac, cb, bd);
        assertThat(paths.getWeight("e")).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(paths.getPath("e")).isNull();
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
