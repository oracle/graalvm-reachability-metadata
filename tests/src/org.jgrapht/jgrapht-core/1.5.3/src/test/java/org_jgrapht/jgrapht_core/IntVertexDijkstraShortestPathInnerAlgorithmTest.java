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
import org.jgrapht.alg.shortestpath.IntVertexDijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntVertexDijkstraShortestPathInnerAlgorithmTest {
    @Test
    void computesShortestPathForDenseIntegerVertexIds() {
        Graph<Integer, DefaultWeightedEdge> graph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultWeightedEdge edge01 = addWeightedEdge(graph, 0, 1, 2.0d);
        addWeightedEdge(graph, 0, 2, 10.0d);
        DefaultWeightedEdge edge12 = addWeightedEdge(graph, 1, 2, 3.0d);
        DefaultWeightedEdge edge23 = addWeightedEdge(graph, 2, 3, 4.0d);

        IntVertexDijkstraShortestPath<DefaultWeightedEdge> algorithm =
            new IntVertexDijkstraShortestPath<>(graph);

        GraphPath<Integer, DefaultWeightedEdge> path = algorithm.getPath(0, 3);

        assertThat(path.getWeight()).isEqualTo(9.0d);
        assertThat(path.getVertexList()).containsExactly(0, 1, 2, 3);
        assertThat(path.getEdgeList()).containsExactly(edge01, edge12, edge23);
    }

    @Test
    void computesSingleSourcePathsForRemappedIntegerVertexIds() {
        Graph<Integer, DefaultWeightedEdge> graph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultWeightedEdge edge10To30 = addWeightedEdge(graph, 10, 30, 7.0d);
        DefaultWeightedEdge edge30To50 = addWeightedEdge(graph, 30, 50, 1.5d);
        addWeightedEdge(graph, 10, 50, 20.0d);
        graph.addVertex(70);

        IntVertexDijkstraShortestPath<DefaultWeightedEdge> algorithm =
            new IntVertexDijkstraShortestPath<>(graph);

        SingleSourcePaths<Integer, DefaultWeightedEdge> paths = algorithm.getPaths(10);
        GraphPath<Integer, DefaultWeightedEdge> pathTo50 = paths.getPath(50);

        assertThat(paths.getSourceVertex()).isEqualTo(10);
        assertThat(paths.getWeight(50)).isEqualTo(8.5d);
        assertThat(pathTo50.getVertexList()).containsExactly(10, 30, 50);
        assertThat(pathTo50.getEdgeList()).containsExactly(edge10To30, edge30To50);
        assertThat(paths.getWeight(70)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(paths.getPath(70)).isNull();
    }

    private static DefaultWeightedEdge addWeightedEdge(
        Graph<Integer, DefaultWeightedEdge> graph, Integer source, Integer target, double weight) {
        graph.addVertex(source);
        graph.addVertex(target);
        DefaultWeightedEdge edge = graph.addEdge(source, target);
        graph.setEdgeWeight(edge, weight);
        return edge;
    }
}
