/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's minimum-spanning-tree subsystem for a network cabling planner.
public class NetworkCablingPlanningTest {
    @Test
    @Timeout(60)
    public void selectsTheLowestCostCablingPlanThatConnectsEverySite() {
        Graph<String, DefaultWeightedEdge> network =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (String site : List.of("datacenter", "north", "south", "east", "west")) {
            network.addVertex(site);
        }
        addCable(network, "datacenter", "north", 4.0);
        addCable(network, "datacenter", "south", 2.0);
        addCable(network, "north", "south", 1.0);
        addCable(network, "north", "east", 5.0);
        addCable(network, "south", "east", 8.0);
        addCable(network, "south", "west", 10.0);
        addCable(network, "east", "west", 2.0);

        SpanningTreeAlgorithm.SpanningTree<DefaultWeightedEdge> plan =
                new KruskalMinimumSpanningTree<>(network).getSpanningTree();

        Set<String> selectedCables = new HashSet<>();
        for (DefaultWeightedEdge cable : plan.getEdges()) {
            selectedCables.add(cableName(network, cable));
        }
        assertEquals(
                Set.of("datacenter-south", "north-south", "east-north", "east-west"),
                selectedCables);
        assertEquals(4, plan.getEdges().size());
        assertEquals(10.0, plan.getWeight());
        assertTrue(plan.getEdges().stream().allMatch(network::containsEdge));
    }

    private static void addCable(
            Graph<String, DefaultWeightedEdge> network,
            String first,
            String second,
            double cost) {
        DefaultWeightedEdge cable = network.addEdge(first, second);
        network.setEdgeWeight(cable, cost);
    }

    private static String cableName(Graph<String, DefaultWeightedEdge> network, DefaultWeightedEdge cable) {
        String first = network.getEdgeSource(cable);
        String second = network.getEdgeTarget(cable);
        return first.compareTo(second) < 0 ? first + "-" + second : second + "-" + first;
    }
}
