/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.flow.EdmondsKarpMFImpl;
import org.jgrapht.alg.interfaces.MaximumFlowAlgorithm;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's maximum-flow subsystem for a capacity planning service.
public class CapacityPlanningTest {
    @Test
    @Timeout(60)
    public void calculatesUsableCapacityAndTheBottleneckCut() {
        Graph<String, DefaultWeightedEdge> network =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (String location : Set.of("source", "warehouse", "airport", "hub", "shop", "customer")) {
            network.addVertex(location);
        }
        addCapacity(network, "source", "warehouse", 10.0);
        addCapacity(network, "source", "airport", 5.0);
        addCapacity(network, "warehouse", "hub", 4.0);
        addCapacity(network, "warehouse", "shop", 8.0);
        addCapacity(network, "airport", "hub", 3.0);
        addCapacity(network, "airport", "shop", 2.0);
        addCapacity(network, "hub", "customer", 5.0);
        addCapacity(network, "shop", "customer", 7.0);

        EdmondsKarpMFImpl<String, DefaultWeightedEdge> planner =
                new EdmondsKarpMFImpl<>(network);
        MaximumFlowAlgorithm.MaximumFlow<DefaultWeightedEdge> plan =
                planner.getMaximumFlow("source", "customer");

        assertEquals(12.0, plan.getValue());
        assertEquals(12.0, planner.getCutCapacity());
        assertTrue(planner.getSourcePartition().contains("source"));
        assertTrue(planner.getSinkPartition().contains("customer"));
        assertEquals(2, planner.getCutEdges().size());
    }

    private static void addCapacity(
            Graph<String, DefaultWeightedEdge> network,
            String from,
            String to,
            double capacity) {
        DefaultWeightedEdge edge = network.addEdge(from, to);
        network.setEdgeWeight(edge, capacity);
    }
}
