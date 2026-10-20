/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.NetworkBuilder;
import com.google.common.graph.Traverser;
import com.google.common.graph.ValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Graph modelling with `com.google.common.graph`: mutable and immutable `Graph`, `ValueGraph`,
 * and `Network` instances, `Graphs` utilities, `EndpointPair`, and `Traverser` walks.
 *
 * A build tool models a multi-module project as a dependency graph. It must compute a build
 * order, find the modules affected by a change, reject dependency cycles, plan artifact
 * shipping between data centers over a weighted link graph, and track parallel replication
 * channels between those data centers.
 */
public class ModuleDependencyGraphTest {

    @Test
    void buildsDependenciesBeforeTheModulesThatNeedThem() {
        MutableGraph<String> modules = ProjectModel.sampleProject();

        assertTrue(modules.isDirected());
        assertFalse(modules.allowsSelfLoops());
        assertFalse(Graphs.hasCycle(modules));
        assertEquals(ImmutableSet.of("core", "json"), modules.successors("storage"));
        assertEquals(ImmutableSet.of("storage", "web"), modules.predecessors("json"));
        assertEquals(0, modules.outDegree("core"));
        assertEquals(3, modules.inDegree("core"));

        List<String> order = ProjectModel.buildOrder(modules);

        assertEquals(modules.nodes(), ImmutableSet.copyOf(order));
        for (EndpointPair<String> dependency : modules.edges()) {
            assertTrue(order.indexOf(dependency.target()) < order.indexOf(dependency.source()),
                    () -> dependency.target() + " must be built before " + dependency.source());
        }
        assertEquals("app", order.get(order.size() - 1));
    }

    @Test
    void findsEveryModuleAffectedByAChange() {
        MutableGraph<String> modules = ProjectModel.sampleProject();

        Set<String> affectedByJson = Graphs.reachableNodes(Graphs.transpose(modules), "json");
        assertEquals(ImmutableSet.of("json", "storage", "web", "app"), affectedByJson);
        assertEquals(ImmutableSet.of("app"), Graphs.reachableNodes(Graphs.transpose(modules), "app"));

        Graph<String> closure = Graphs.transitiveClosure(
                modules, Graphs.TransitiveClosureSelfLoopStrategy.ADD_SELF_LOOPS_FOR_CYCLES);
        assertTrue(closure.hasEdgeConnecting("app", "core"));
        assertFalse(closure.hasEdgeConnecting("core", "core"));
        assertTrue(closure.hasEdgeConnecting("web", "core"));
        assertFalse(closure.hasEdgeConnecting("core", "app"));
        assertFalse(closure.hasEdgeConnecting("json", "storage"));

        List<String> breadthFirst = ImmutableList.copyOf(Traverser.forGraph(modules).breadthFirst("app"));
        assertEquals(ImmutableList.of("app", "web", "storage", "json", "core"), breadthFirst);
    }

    @Test
    void rejectsSelfDependenciesAndDetectsCycles() {
        MutableGraph<String> modules = ProjectModel.sampleProject();

        assertThrows(IllegalArgumentException.class, () -> modules.putEdge("web", "web"));
        assertFalse(modules.putEdge("web", "json"));

        assertTrue(modules.putEdge("core", "app"));
        assertTrue(Graphs.hasCycle(modules));
        assertThrows(IllegalStateException.class, () -> ProjectModel.buildOrder(modules));

        assertTrue(modules.removeEdge("core", "app"));
        assertFalse(Graphs.hasCycle(modules));

        assertTrue(modules.removeNode("json"));
        assertFalse(modules.nodes().contains("json"));
        assertEquals(ImmutableSet.of("core"), modules.successors("storage"));
        assertEquals(ImmutableSet.of("core"), modules.successors("web"));
    }

    @Test
    void freezesASubsetOfTheProjectAsAnImmutableGraph() {
        MutableGraph<String> modules = ProjectModel.sampleProject();

        ImmutableGraph<String> backend = ImmutableGraph.copyOf(
                Graphs.inducedSubgraph(modules, ImmutableSet.of("storage", "json", "core")));
        ImmutableGraph<String> expected = GraphBuilder.directed()
                .<String>immutable()
                .putEdge("storage", "core")
                .putEdge("storage", "json")
                .putEdge("json", "core")
                .build();

        assertEquals(expected, backend);
        assertEquals(3, backend.edges().size());
        assertTrue(backend.edges().contains(EndpointPair.ordered("json", "core")));
        assertFalse(backend.edges().contains(EndpointPair.ordered("core", "json")));
        assertNotEquals(expected, ImmutableGraph.copyOf(Graphs.transpose(expected)));
        assertEquals(backend, ImmutableGraph.copyOf(Graphs.transpose(Graphs.transpose(backend))));

        MutableGraph<String> working = Graphs.copyOf(backend);
        working.putEdge("core", "logging");
        assertEquals(4, working.nodes().size());
        assertEquals(3, backend.nodes().size());
    }

    @Test
    void plansTheCheapestArtifactShippingRouteBetweenDataCenters() {
        MutableValueGraph<String, Integer> links = ValueGraphBuilder.undirected()
                .nodeOrder(ElementOrder.<String>insertion())
                .build();
        links.putEdgeValue("ams", "fra", 7);
        links.putEdgeValue("fra", "waw", 9);
        links.putEdgeValue("ams", "lon", 4);
        links.putEdgeValue("lon", "waw", 30);
        links.putEdgeValue("fra", "mil", 6);
        links.putEdgeValue("mil", "waw", 2);

        assertFalse(links.isDirected());
        assertEquals(Optional.of(7), links.edgeValue("fra", "ams"));
        assertEquals(Optional.empty(), links.edgeValue("ams", "waw"));
        assertEquals(-1, links.edgeValueOrDefault("lon", "mil", -1));
        assertEquals(ImmutableSet.of("fra", "lon", "mil"), links.adjacentNodes("waw"));
        assertEquals(EndpointPair.unordered("waw", "mil"), EndpointPair.unordered("mil", "waw"));

        assertEquals(30, links.putEdgeValue("waw", "lon", 25));
        ShippingPlanner planner = new ShippingPlanner(ImmutableValueGraph.copyOf(links));
        assertEquals(new ShippingPlanner.Route(ImmutableList.of("ams", "fra", "mil", "waw"), 15),
                planner.cheapestRoute("ams", "waw"));

        links.removeEdge("fra", "mil");
        ShippingPlanner degraded = new ShippingPlanner(links);
        assertEquals(new ShippingPlanner.Route(ImmutableList.of("ams", "fra", "waw"), 16),
                degraded.cheapestRoute("ams", "waw"));
        assertEquals(new ShippingPlanner.Route(ImmutableList.of("ams", "fra", "mil", "waw"), 15),
                planner.cheapestRoute("ams", "waw"));
    }

    @Test
    void tracksParallelReplicationChannelsBetweenDataCenters() {
        MutableNetwork<String, String> channels = NetworkBuilder.directed()
                .allowsParallelEdges(true)
                .edgeOrder(ElementOrder.<String>insertion())
                .build();
        channels.addEdge("ams", "fra", "ams-fra-primary");
        channels.addEdge("ams", "fra", "ams-fra-backup");
        channels.addEdge("fra", "ams", "fra-ams-primary");
        channels.addEdge("fra", "waw", "fra-waw-primary");

        assertTrue(channels.allowsParallelEdges());
        assertEquals(ImmutableSet.of("ams-fra-primary", "ams-fra-backup"), channels.edgesConnecting("ams", "fra"));
        assertEquals(ImmutableSet.of("fra-ams-primary"), channels.edgesConnecting("fra", "ams"));
        assertEquals(EndpointPair.ordered("fra", "waw"), channels.incidentNodes("fra-waw-primary"));
        assertEquals(ImmutableSet.of("fra-ams-primary", "fra-waw-primary"), channels.outEdges("fra"));
        assertEquals(3, channels.degree("ams"));
        assertEquals(ImmutableSet.of("ams-fra-backup"), channels.adjacentEdges("ams-fra-primary").stream()
                .filter(edge -> channels.incidentNodes(edge).source().equals("ams"))
                .collect(ImmutableSet.toImmutableSet()));
        assertThrows(IllegalArgumentException.class, () -> channels.addEdge("ams", "waw", "ams-fra-backup"));

        ImmutableNetwork<String, String> snapshot = ImmutableNetwork.copyOf(channels);
        assertTrue(channels.removeEdge("ams-fra-primary"));
        assertEquals(ImmutableSet.of("ams-fra-backup"), channels.edgesConnecting("ams", "fra"));
        assertEquals(4, snapshot.edges().size());
        assertTrue(snapshot.asGraph().hasEdgeConnecting("ams", "fra"));
        assertEquals(ImmutableSet.of("waw"), Graphs.reachableNodes(snapshot.asGraph(), "waw"));
        assertEquals(ImmutableSet.of("ams", "fra", "waw"), Graphs.reachableNodes(snapshot.asGraph(), "ams"));
    }

    /** Builds the sample project and orders its modules for a build. */
    static final class ProjectModel {

        static MutableGraph<String> sampleProject() {
            MutableGraph<String> modules = GraphBuilder.directed()
                    .allowsSelfLoops(false)
                    .nodeOrder(ElementOrder.<String>insertion())
                    .incidentEdgeOrder(ElementOrder.<String>stable())
                    .build();
            modules.addNode("logging");
            modules.putEdge("app", "web");
            modules.putEdge("app", "storage");
            modules.putEdge("web", "json");
            modules.putEdge("web", "core");
            modules.putEdge("storage", "core");
            modules.putEdge("storage", "json");
            modules.putEdge("json", "core");
            modules.removeNode("logging");
            return modules;
        }

        /** Kahn's algorithm over dependency edges: a module is ready once its dependencies are built. */
        static List<String> buildOrder(Graph<String> modules) {
            Map<String, Integer> pending = new HashMap<>();
            List<String> ready = new ArrayList<>();
            for (String module : modules.nodes()) {
                pending.put(module, modules.outDegree(module));
                if (modules.outDegree(module) == 0) {
                    ready.add(module);
                }
            }
            List<String> order = new ArrayList<>();
            while (!ready.isEmpty()) {
                String built = ready.remove(0);
                order.add(built);
                for (String dependent : modules.predecessors(built)) {
                    if (pending.merge(dependent, -1, Integer::sum) == 0) {
                        ready.add(dependent);
                    }
                }
            }
            if (order.size() != modules.nodes().size()) {
                throw new IllegalStateException("Dependency cycle among " + modules.nodes());
            }
            return order;
        }
    }

    /** Finds cheapest routes over a weighted, undirected link graph. */
    static final class ShippingPlanner {

        record Route(List<String> hops, int cost) {
        }

        private final ValueGraph<String, Integer> links;

        ShippingPlanner(ValueGraph<String, Integer> links) {
            this.links = links;
        }

        Route cheapestRoute(String from, String to) {
            Map<String, Integer> cost = new HashMap<>();
            Map<String, String> previous = new HashMap<>();
            PriorityQueue<String> queue = new PriorityQueue<>((a, b) -> Integer.compare(cost.get(a), cost.get(b)));
            cost.put(from, 0);
            queue.add(from);
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(to)) {
                    break;
                }
                for (EndpointPair<String> link : links.incidentEdges(current)) {
                    String next = link.adjacentNode(current);
                    int candidate = cost.get(current) + links.edgeValue(link).orElseThrow();
                    if (candidate < cost.getOrDefault(next, Integer.MAX_VALUE)) {
                        queue.remove(next);
                        cost.put(next, candidate);
                        previous.put(next, current);
                        queue.add(next);
                    }
                }
            }
            List<String> hops = new ArrayList<>();
            for (String hop = to; hop != null; hop = previous.get(hop)) {
                hops.add(0, hop);
            }
            return new Route(ImmutableList.copyOf(hops), cost.get(to));
        }
    }
}
