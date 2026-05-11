/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.Coreness;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CorenessTest {
    @Test
    void computesScoresForGraphWithTriangleTailAndIsolatedVertex() {
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        addVertices(graph, "a", "b", "c", "d", "e", "isolated");
        addEdges(graph, "a", "b", "b", "c", "c", "a", "c", "d", "d", "e");

        Coreness<String, DefaultEdge> coreness = new Coreness<>(graph);

        Map<String, Integer> scores = coreness.getScores();

        assertThat(scores)
            .containsEntry("isolated", 0)
            .containsEntry("e", 1)
            .containsEntry("d", 1)
            .containsEntry("a", 2)
            .containsEntry("b", 2)
            .containsEntry("c", 2);
        assertThat(coreness.getDegeneracy()).isEqualTo(2);
        assertThat(coreness.getVertexScore("c")).isEqualTo(2);
    }

    @Test
    void rejectsScoreLookupForUnknownVertex() {
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        addVertices(graph, "known");

        Coreness<String, DefaultEdge> coreness = new Coreness<>(graph);

        assertThatThrownBy(() -> coreness.getVertexScore("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Cannot return score of unknown vertex");
    }

    private static void addVertices(Graph<String, DefaultEdge> graph, String... vertices) {
        for (String vertex : vertices) {
            graph.addVertex(vertex);
        }
    }

    private static void addEdges(Graph<String, DefaultEdge> graph, String... vertices) {
        for (int i = 0; i < vertices.length; i += 2) {
            graph.addEdge(vertices[i], vertices[i + 1]);
        }
    }
}
