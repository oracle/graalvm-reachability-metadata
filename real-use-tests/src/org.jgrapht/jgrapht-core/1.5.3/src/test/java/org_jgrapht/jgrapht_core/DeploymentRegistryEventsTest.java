/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.event.GraphEdgeChangeEvent;
import org.jgrapht.event.GraphListener;
import org.jgrapht.event.GraphVertexChangeEvent;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JGraphT's graph event listener subsystem for a live deployment registry.
public class DeploymentRegistryEventsTest {
    @Test
    @Timeout(60)
    public void recordsDeploymentTopologyChangesAsTheyHappen() {
        DefaultListenableGraph<String, DefaultWeightedEdge> topology = new DefaultListenableGraph<>(
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        List<String> changes = new ArrayList<>();
        topology.addGraphListener(new DeploymentChangeRecorder(changes));

        topology.addVertex("api");
        topology.addVertex("database");
        DefaultWeightedEdge dependency = topology.addEdge("api", "database");
        topology.setEdgeWeight(dependency, 2.5);
        topology.removeEdge(dependency);
        topology.removeVertex("database");

        assertEquals(
                List.of(
                        "vertex-added:api",
                        "vertex-added:database",
                        "edge-added:api->database:1.0",
                        "edge-weight-updated:api->database:2.5",
                        "edge-removed:api->database:2.5",
                        "vertex-removed:database"),
                changes);
    }

    private static final class DeploymentChangeRecorder
            implements GraphListener<String, DefaultWeightedEdge> {
        private final List<String> changes;

        private DeploymentChangeRecorder(List<String> changes) {
            this.changes = changes;
        }

        @Override
        public void vertexAdded(GraphVertexChangeEvent<String> event) {
            changes.add("vertex-added:" + event.getVertex());
        }

        @Override
        public void vertexRemoved(GraphVertexChangeEvent<String> event) {
            changes.add("vertex-removed:" + event.getVertex());
        }

        @Override
        public void edgeAdded(GraphEdgeChangeEvent<String, DefaultWeightedEdge> event) {
            changes.add(edgeChange("edge-added", event));
        }

        @Override
        public void edgeRemoved(GraphEdgeChangeEvent<String, DefaultWeightedEdge> event) {
            changes.add(edgeChange("edge-removed", event));
        }

        @Override
        public void edgeWeightUpdated(GraphEdgeChangeEvent<String, DefaultWeightedEdge> event) {
            changes.add(edgeChange("edge-weight-updated", event));
        }

        private static String edgeChange(
                String action, GraphEdgeChangeEvent<String, DefaultWeightedEdge> event) {
            return action + ":" + event.getEdgeSource() + "->" + event.getEdgeTarget()
                    + ":" + event.getEdgeWeight();
        }
    }
}
