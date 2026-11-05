/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.tour.HeldKarpTSP;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's traveling-salesperson subsystem for a field service route planner.
public class FieldServiceRoutingTest {
    @Test
    @Timeout(60)
    public void plansTheLowestCostRoundTripThroughEveryAppointment() {
        SimpleWeightedGraph<String, DefaultWeightedEdge> appointments =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (String appointment : Set.of("hub", "alpha", "bravo", "charlie", "delta")) {
            appointments.addVertex(appointment);
        }
        addTravelTime(appointments, "hub", "alpha", 2.0);
        addTravelTime(appointments, "hub", "bravo", 9.0);
        addTravelTime(appointments, "hub", "charlie", 10.0);
        addTravelTime(appointments, "hub", "delta", 7.0);
        addTravelTime(appointments, "alpha", "bravo", 3.0);
        addTravelTime(appointments, "alpha", "charlie", 8.0);
        addTravelTime(appointments, "alpha", "delta", 9.0);
        addTravelTime(appointments, "bravo", "charlie", 4.0);
        addTravelTime(appointments, "bravo", "delta", 8.0);
        addTravelTime(appointments, "charlie", "delta", 5.0);

        GraphPath<String, DefaultWeightedEdge> route = new HeldKarpTSP<String, DefaultWeightedEdge>()
                .getTour(appointments);

        assertEquals(21.0, route.getWeight());
        assertEquals(route.getStartVertex(), route.getEndVertex());
        assertEquals(6, route.getVertexList().size());
        assertEquals(
                Set.of("hub", "alpha", "bravo", "charlie", "delta"),
                new HashSet<>(route.getVertexList()));
        assertTrue(route.getVertexList().contains("hub"));
    }

    private static void addTravelTime(
            SimpleWeightedGraph<String, DefaultWeightedEdge> appointments,
            String first,
            String second,
            double minutes) {
        DefaultWeightedEdge connection = appointments.addEdge(first, second);
        appointments.setEdgeWeight(connection, minutes);
    }
}
