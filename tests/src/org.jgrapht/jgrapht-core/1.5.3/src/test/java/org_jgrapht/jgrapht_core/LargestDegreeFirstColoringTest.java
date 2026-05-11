/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.color.LargestDegreeFirstColoring;
import org.jgrapht.alg.interfaces.VertexColoringAlgorithm.Coloring;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LargestDegreeFirstColoringTest {
    @Test
    void colorsStarGraphUsingLargestDegreeFirstOrdering() {
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        addEdges(
            graph,
            "center", "leaf-a",
            "center", "leaf-b",
            "center", "leaf-c",
            "center", "leaf-d");

        LargestDegreeFirstColoring<String, DefaultEdge> algorithm =
            new LargestDegreeFirstColoring<>(graph);

        Coloring<String> coloring = algorithm.getColoring();
        Map<String, Integer> colors = coloring.getColors();

        assertThat(coloring.getNumberColors()).isEqualTo(2);
        assertThat(colors).containsOnlyKeys("center", "leaf-a", "leaf-b", "leaf-c", "leaf-d");
        assertThat(colors.get("leaf-a")).isEqualTo(colors.get("leaf-b"));
        assertThat(colors.get("leaf-a")).isEqualTo(colors.get("leaf-c"));
        assertThat(colors.get("leaf-a")).isEqualTo(colors.get("leaf-d"));
        assertThat(colors.get("center")).isNotEqualTo(colors.get("leaf-a"));
    }

    private static void addEdges(Graph<String, DefaultEdge> graph, String... vertices) {
        for (int i = 0; i < vertices.length; i += 2) {
            graph.addVertex(vertices[i]);
            graph.addVertex(vertices[i + 1]);
            graph.addEdge(vertices[i], vertices[i + 1]);
        }
    }
}
