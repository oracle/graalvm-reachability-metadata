/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises JGraphT's strong-connectivity inspection subsystem for a service dependency analyzer.
public class ServiceDependencyAnalysisTest {
    @Test
    @Timeout(60)
    public void groupsServicesThatParticipateInDependencyCycles() {
        Graph<String, DefaultEdge> dependencies =
                new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String service : List.of(
                "cart", "checkout", "inventory", "catalog", "search", "payment", "receipt")) {
            dependencies.addVertex(service);
        }
        dependencies.addEdge("cart", "checkout");
        dependencies.addEdge("checkout", "inventory");
        dependencies.addEdge("inventory", "checkout");
        dependencies.addEdge("catalog", "search");
        dependencies.addEdge("search", "catalog");
        dependencies.addEdge("checkout", "payment");
        dependencies.addEdge("payment", "receipt");
        dependencies.addEdge("receipt", "payment");

        GabowStrongConnectivityInspector<String, DefaultEdge> inspector =
                new GabowStrongConnectivityInspector<>(dependencies);

        List<Set<String>> components = inspector.stronglyConnectedSets();

        assertFalse(inspector.isStronglyConnected());
        assertEquals(
                Set.of(
                        Set.of("cart"),
                        Set.of("checkout", "inventory"),
                        Set.of("catalog", "search"),
                        Set.of("payment", "receipt")),
                new HashSet<>(components));
        assertEquals(4, components.size());
    }
}
