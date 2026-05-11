/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.Pseudograph;
import org.jgrapht.traverse.DegeneracyOrderingIterator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DegeneracyOrderingIteratorTest {
    @Test
    void traversesVerticesInDegeneracyOrderAndIgnoresSelfLoops() {
        Graph<String, DefaultEdge> graph = new Pseudograph<>(DefaultEdge.class);
        addVertices(graph, "loop", "leaf", "hub", "other", "tail");
        graph.addEdge("loop", "loop");
        graph.addEdge("leaf", "hub");
        graph.addEdge("hub", "other");
        graph.addEdge("other", "tail");

        DegeneracyOrderingIterator<String, DefaultEdge> iterator =
            new DegeneracyOrderingIterator<>(graph);

        assertThat(iterator.isCrossComponentTraversal()).isTrue();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.hasNext()).isTrue();

        List<String> ordering = new ArrayList<>();
        while (iterator.hasNext()) {
            ordering.add(iterator.next());
        }

        assertThat(ordering).first().isEqualTo("loop");
        assertThat(ordering).containsExactlyInAnyOrder("loop", "leaf", "hub", "other", "tail");
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void remainsAlwaysCrossComponentTraversal() {
        Graph<String, DefaultEdge> graph = new Pseudograph<>(DefaultEdge.class);
        graph.addVertex("only");
        DegeneracyOrderingIterator<String, DefaultEdge> iterator =
            new DegeneracyOrderingIterator<>(graph);

        iterator.setCrossComponentTraversal(true);

        assertThat(iterator.isCrossComponentTraversal()).isTrue();
        assertThatThrownBy(() -> iterator.setCrossComponentTraversal(false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Iterator is always cross-component");
    }

    private static void addVertices(Graph<String, DefaultEdge> graph, String... vertices) {
        for (String vertex : vertices) {
            graph.addVertex(vertex);
        }
    }
}
