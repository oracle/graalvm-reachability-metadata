/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.GraphMapping;
import org.jgrapht.alg.isomorphism.AHURootedTreeIsomorphismInspector;
import org.jgrapht.alg.isomorphism.IsomorphicGraphMapping;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class AHURootedTreeIsomorphismInspectorTest {
    @Test
    void detectsIsomorphismForRootedTrees() {
        Graph<String, DefaultEdge> firstTree = new SimpleGraph<>(DefaultEdge.class);
        addEdges(firstTree, "root", "left", "root", "right", "left", "leaf");

        Graph<String, DefaultEdge> secondTree = new SimpleGraph<>(DefaultEdge.class);
        addEdges(secondTree, "top", "child-a", "top", "child-b", "child-a", "terminal");

        AHURootedTreeIsomorphismInspector<String, DefaultEdge> inspector =
            new AHURootedTreeIsomorphismInspector<>(firstTree, "root", secondTree, "top");

        assertThat(inspector.isomorphismExists()).isTrue();

        IsomorphicGraphMapping<String, DefaultEdge> mapping = inspector.getMapping();
        assertThat(mapping).isNotNull();
        assertThat(mapping.getVertexCorrespondence("root", true)).isEqualTo("top");
        assertThat(mapping.getVertexCorrespondence("top", false)).isEqualTo("root");

        Iterator<GraphMapping<String, DefaultEdge>> mappings = inspector.getMappings();
        assertThat(mappings.hasNext()).isTrue();
        GraphMapping<String, DefaultEdge> onlyMapping = mappings.next();
        assertThat(onlyMapping.getVertexCorrespondence("root", true)).isEqualTo("top");
        assertThat(mappings.hasNext()).isFalse();
    }

    private static void addEdges(Graph<String, DefaultEdge> graph, String... vertices) {
        for (int i = 0; i < vertices.length; i += 2) {
            graph.addVertex(vertices[i]);
            graph.addVertex(vertices[i + 1]);
            graph.addEdge(vertices[i], vertices[i + 1]);
        }
    }
}
