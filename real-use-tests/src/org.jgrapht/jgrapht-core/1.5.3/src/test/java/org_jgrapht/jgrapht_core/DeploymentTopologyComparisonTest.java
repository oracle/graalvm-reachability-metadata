/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.GraphMapping;
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Comparator;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's graph-isomorphism subsystem for comparing deployment topologies.
public class DeploymentTopologyComparisonTest {
    @Test
    @Timeout(60)
    public void matchesRenamedServicesWhenTheirTopologyIsUnchanged() {
        Graph<Service, DefaultEdge> current = topology(
                new Service("checkout-api", "api"),
                new Service("checkout-db", "database"),
                new Service("checkout-cache", "cache"));
        Graph<Service, DefaultEdge> candidate = topology(
                new Service("orders-api", "api"),
                new Service("orders-db", "database"),
                new Service("orders-cache", "cache"));

        VF2GraphIsomorphismInspector<Service, DefaultEdge> comparison =
                new VF2GraphIsomorphismInspector<>(
                        current, candidate, Comparator.comparing(Service::role), null);

        assertTrue(comparison.isomorphismExists());
        Iterator<GraphMapping<Service, DefaultEdge>> mappings = comparison.getMappings();
        assertTrue(mappings.hasNext());
        GraphMapping<Service, DefaultEdge> mapping = mappings.next();
        Service currentApi = new Service("checkout-api", "api");
        Service candidateApi = mapping.getVertexCorrespondence(currentApi, true);
        assertNotNull(candidateApi);
        assertEquals("orders-api", candidateApi.name());
        assertEquals("api", candidateApi.role());
    }

    @Test
    @Timeout(60)
    public void rejectsATopologyWithAnAdditionalDependency() {
        Graph<Service, DefaultEdge> baseline = topology(
                new Service("checkout-api", "api"),
                new Service("checkout-db", "database"),
                new Service("checkout-cache", "cache"));
        Graph<Service, DefaultEdge> changed = topology(
                new Service("orders-api", "api"),
                new Service("orders-db", "database"),
                new Service("orders-cache", "cache"));
        Service candidateApi = new Service("orders-api", "api");
        Service candidateCache = new Service("orders-cache", "cache");
        changed.addEdge(candidateCache, candidateApi);

        VF2GraphIsomorphismInspector<Service, DefaultEdge> comparison =
                new VF2GraphIsomorphismInspector<>(
                        baseline, changed, Comparator.comparing(Service::role), null);

        assertFalse(comparison.isomorphismExists());
    }

    private static Graph<Service, DefaultEdge> topology(
            Service api, Service database, Service cache) {
        Graph<Service, DefaultEdge> topology = new DefaultDirectedGraph<>(DefaultEdge.class);
        topology.addVertex(api);
        topology.addVertex(database);
        topology.addVertex(cache);
        topology.addEdge(api, database);
        topology.addEdge(api, cache);
        return topology;
    }

    private record Service(String name, String role) {}
}
