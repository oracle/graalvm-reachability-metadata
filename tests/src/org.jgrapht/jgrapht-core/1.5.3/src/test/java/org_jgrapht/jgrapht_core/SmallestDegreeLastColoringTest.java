/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.color.SmallestDegreeLastColoring;
import org.jgrapht.alg.interfaces.VertexColoringAlgorithm.Coloring;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SmallestDegreeLastColoringTest {
    @Test
    void colorsWheelGraphUsingSmallestDegreeLastOrdering() {
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        addCycle(graph, "rim-a", "rim-b", "rim-c", "rim-d", "rim-e");
        addEdges(
            graph,
            "hub", "rim-a",
            "hub", "rim-b",
            "hub", "rim-c",
            "hub", "rim-d",
            "hub", "rim-e");

        SmallestDegreeLastColoring<String, DefaultEdge> algorithm =
            new SmallestDegreeLastColoring<>(graph);

        Coloring<String> coloring = algorithm.getColoring();
        Map<String, Integer> colors = coloring.getColors();

        assertThat(colors).containsOnlyKeys("hub", "rim-a", "rim-b", "rim-c", "rim-d", "rim-e");
        assertThat(coloring.getNumberColors()).isEqualTo(4);
        for (DefaultEdge edge : graph.edgeSet()) {
            String source = graph.getEdgeSource(edge);
            String target = graph.getEdgeTarget(edge);
            assertThat(colors.get(source)).isNotEqualTo(colors.get(target));
        }
    }

    private static void addCycle(Graph<String, DefaultEdge> graph, String... vertices) {
        for (String vertex : vertices) {
            graph.addVertex(vertex);
        }
        for (int i = 0; i < vertices.length; i++) {
            graph.addEdge(vertices[i], vertices[(i + 1) % vertices.length]);
        }
    }

    private static void addEdges(Graph<String, DefaultEdge> graph, String... vertices) {
        for (int i = 0; i < vertices.length; i += 2) {
            graph.addVertex(vertices[i]);
            graph.addVertex(vertices[i + 1]);
            graph.addEdge(vertices[i], vertices[i + 1]);
        }
    }
}
