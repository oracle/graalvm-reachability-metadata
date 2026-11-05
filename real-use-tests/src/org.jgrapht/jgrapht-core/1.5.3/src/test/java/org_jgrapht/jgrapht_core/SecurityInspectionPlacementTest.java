/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.VertexCoverAlgorithm;
import org.jgrapht.alg.vertexcover.RecursiveExactVCImpl;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's vertex-cover subsystem for placing network inspection agents.
public class SecurityInspectionPlacementTest {
    @Test
    @Timeout(60)
    public void placesTheSmallestSetOfInspectionAgents() {
        Graph<String, DefaultEdge> communications = new SimpleGraph<>(DefaultEdge.class);
        for (String service : Set.of("gateway", "billing", "catalog", "identity")) {
            communications.addVertex(service);
        }
        connect(communications, "gateway", "billing");
        connect(communications, "gateway", "catalog");
        connect(communications, "gateway", "identity");

        VertexCoverAlgorithm.VertexCover<String> placement =
                new RecursiveExactVCImpl<String, DefaultEdge>(communications).getVertexCover();

        assertEquals(Set.of("gateway"), placement);
        assertEquals(1.0, placement.getWeight());
        assertTrue(communications.edgeSet().stream().allMatch(edge ->
                placement.contains(communications.getEdgeSource(edge))
                        || placement.contains(communications.getEdgeTarget(edge))));
    }

    private static void connect(Graph<String, DefaultEdge> communications, String first, String second) {
        communications.addEdge(first, second);
    }
}
