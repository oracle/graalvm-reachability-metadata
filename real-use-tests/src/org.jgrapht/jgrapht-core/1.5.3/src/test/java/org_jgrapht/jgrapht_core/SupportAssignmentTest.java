/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.MatchingAlgorithm;
import org.jgrapht.alg.matching.MaximumWeightBipartiteMatching;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JGraphT's bipartite matching subsystem for assigning support agents to customer incidents.
public class SupportAssignmentTest {
    @Test
    @Timeout(60)
    public void assignsEachIncidentToTheMostValuableAvailableAgent() {
        Graph<String, DefaultWeightedEdge> assignments =
                new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Set<String> agents = Set.of("alice", "bob", "carol");
        Set<String> incidents = Set.of("billing", "outage", "account");
        agents.forEach(assignments::addVertex);
        incidents.forEach(assignments::addVertex);
        addOffer(assignments, "alice", "billing", 10.0);
        addOffer(assignments, "alice", "outage", 2.0);
        addOffer(assignments, "bob", "billing", 8.0);
        addOffer(assignments, "bob", "outage", 9.0);
        addOffer(assignments, "bob", "account", 1.0);
        addOffer(assignments, "carol", "outage", 7.0);
        addOffer(assignments, "carol", "account", 11.0);

        MatchingAlgorithm.Matching<String, DefaultWeightedEdge> assignment =
                new MaximumWeightBipartiteMatching<>(assignments, agents, incidents).getMatching();

        Map<String, String> selectedIncidents = new HashMap<>();
        for (DefaultWeightedEdge edge : assignment.getEdges()) {
            String agent = agents.contains(assignments.getEdgeSource(edge))
                    ? assignments.getEdgeSource(edge)
                    : assignments.getEdgeTarget(edge);
            String incident = incidents.contains(assignments.getEdgeSource(edge))
                    ? assignments.getEdgeSource(edge)
                    : assignments.getEdgeTarget(edge);
            selectedIncidents.put(agent, incident);
        }
        assertEquals(Map.of("alice", "billing", "bob", "outage", "carol", "account"), selectedIncidents);
        assertEquals(30.0, assignment.getWeight());
        assertEquals(3, assignment.getEdges().size());
    }

    private static void addOffer(
            Graph<String, DefaultWeightedEdge> assignments,
            String agent,
            String incident,
            double value) {
        DefaultWeightedEdge edge = assignments.addEdge(agent, incident);
        assignments.setEdgeWeight(edge, value);
    }
}
