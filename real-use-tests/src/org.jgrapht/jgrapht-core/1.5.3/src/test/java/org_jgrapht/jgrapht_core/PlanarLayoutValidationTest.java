/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's planarity-inspection subsystem for a campus layout validator.
public class PlanarLayoutValidationTest {
    @Test
    @Timeout(60)
    public void acceptsAPlanarCampusLayout() {
        Graph<String, DefaultEdge> campus = new SimpleGraph<>(DefaultEdge.class);
        for (String building : List.of("library", "science", "arts", "stadium", "residence")) {
            campus.addVertex(building);
        }
        connect(campus, "library", "science");
        connect(campus, "science", "stadium");
        connect(campus, "stadium", "residence");
        connect(campus, "residence", "arts");
        connect(campus, "arts", "library");
        connect(campus, "library", "stadium");

        BoyerMyrvoldPlanarityInspector<String, DefaultEdge> validator =
                new BoyerMyrvoldPlanarityInspector<>(campus);

        assertTrue(validator.isPlanar());
    }

    @Test
    @Timeout(60)
    public void rejectsAUtilityLayoutThatRequiresCrossingConnections() {
        Graph<String, DefaultEdge> utilityNetwork = new SimpleGraph<>(DefaultEdge.class);
        List<String> substations = List.of("north", "south", "east");
        List<String> neighborhoods = List.of("alpha", "bravo", "charlie");
        substations.forEach(utilityNetwork::addVertex);
        neighborhoods.forEach(utilityNetwork::addVertex);
        for (String substation : substations) {
            for (String neighborhood : neighborhoods) {
                connect(utilityNetwork, substation, neighborhood);
            }
        }

        BoyerMyrvoldPlanarityInspector<String, DefaultEdge> validator =
                new BoyerMyrvoldPlanarityInspector<>(utilityNetwork);

        assertFalse(validator.isPlanar());
    }

    private static void connect(Graph<String, DefaultEdge> graph, String first, String second) {
        graph.addEdge(first, second);
    }
}
