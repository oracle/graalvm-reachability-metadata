/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises JGraphT's weighted shortest-path subsystem for a delivery route planner.
public class DeliveryRoutePlanningTest {
    @Test
    @Timeout(60)
    public void selectsTheFastestRouteAndReportsItsTravelTime() {
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> routes =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (String location : List.of("depot", "crossing", "market", "station", "customer")) {
            routes.addVertex(location);
        }
        addRoute(routes, "depot", "crossing", 4.0);
        addRoute(routes, "crossing", "market", 3.0);
        addRoute(routes, "market", "customer", 5.0);
        addRoute(routes, "depot", "station", 2.0);
        addRoute(routes, "station", "market", 6.0);
        addRoute(routes, "crossing", "station", 1.0);
        addRoute(routes, "station", "customer", 12.0);

        GraphPath<String, DefaultWeightedEdge> route =
                new DijkstraShortestPath<>(routes).getPath("depot", "customer");

        assertFalse(route == null, "A customer route should be available");
        assertEquals(List.of("depot", "crossing", "market", "customer"), route.getVertexList());
        assertEquals(12.0, route.getWeight());
        assertEquals(3, route.getLength());
    }

    private static void addRoute(
            DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> routes,
            String from,
            String to,
            double travelTime) {
        DefaultWeightedEdge edge = routes.addEdge(from, to);
        routes.setEdgeWeight(edge, travelTime);
    }
}
