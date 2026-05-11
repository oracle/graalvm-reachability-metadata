/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.interfaces.PlanarityTestingAlgorithm.Embedding;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BoyerMyrvoldPlanarityInspectorInnerNodeTest {
    @Test
    void computesPlanarEmbeddingForConnectedGraph() {
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        Graphs.addAllVertices(graph, List.of("a", "b", "c", "d"));
        graph.addEdge("a", "b");
        graph.addEdge("b", "c");
        graph.addEdge("c", "d");
        graph.addEdge("d", "a");
        graph.addEdge("a", "c");

        BoyerMyrvoldPlanarityInspector<String, DefaultEdge> inspector =
            new BoyerMyrvoldPlanarityInspector<>(graph);

        assertThat(inspector.isPlanar()).isTrue();
        Embedding<String, DefaultEdge> embedding = inspector.getEmbedding();

        assertThat(embedding.getGraph()).isSameAs(graph);
        assertThat(embedding.getEdgesAround("a")).hasSize(3);
    }
}
