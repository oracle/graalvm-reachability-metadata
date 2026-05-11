/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.color.SaturationDegreeColoring;
import org.jgrapht.alg.interfaces.VertexColoringAlgorithm.Coloring;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SaturationDegreeColoringTest {
    @Test
    void colorsOddCycleUsingSaturationDegreeHeap() {
        Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        addCycle(graph, 0, 1, 2, 3, 4);

        SaturationDegreeColoring<Integer, DefaultEdge> algorithm =
            new SaturationDegreeColoring<>(graph);

        Coloring<Integer> coloring = algorithm.getColoring();
        Map<Integer, Integer> colors = coloring.getColors();

        assertThat(colors).containsOnlyKeys(0, 1, 2, 3, 4);
        assertThat(coloring.getNumberColors()).isEqualTo(3);
        for (DefaultEdge edge : graph.edgeSet()) {
            Integer source = graph.getEdgeSource(edge);
            Integer target = graph.getEdgeTarget(edge);
            assertThat(colors.get(source)).isNotEqualTo(colors.get(target));
        }
    }

    private static void addCycle(Graph<Integer, DefaultEdge> graph, Integer... vertices) {
        for (Integer vertex : vertices) {
            graph.addVertex(vertex);
        }
        for (int i = 0; i < vertices.length; i++) {
            graph.addEdge(vertices[i], vertices[(i + 1) % vertices.length]);
        }
    }
}
