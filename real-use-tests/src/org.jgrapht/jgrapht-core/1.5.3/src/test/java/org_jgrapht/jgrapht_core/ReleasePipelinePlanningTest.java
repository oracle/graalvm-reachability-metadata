/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's topological-order traversal subsystem for a release pipeline.
public class ReleasePipelinePlanningTest {
    @Test
    @Timeout(60)
    public void schedulesEachReleaseStepAfterItsPrerequisites() {
        DirectedAcyclicGraph<String, DefaultEdge> pipeline =
                new DirectedAcyclicGraph<>(DefaultEdge.class);
        for (String step : List.of("compile", "lint", "package", "integration-test", "publish")) {
            pipeline.addVertex(step);
        }
        pipeline.addEdge("compile", "package");
        pipeline.addEdge("compile", "integration-test");
        pipeline.addEdge("lint", "publish");
        pipeline.addEdge("package", "publish");
        pipeline.addEdge("integration-test", "publish");

        List<String> executionOrder = new ArrayList<>();
        TopologicalOrderIterator<String, DefaultEdge> scheduler =
                new TopologicalOrderIterator<>(pipeline, String::compareTo);
        scheduler.forEachRemaining(executionOrder::add);

        assertEquals(
                List.of("compile", "integration-test", "lint", "package", "publish"),
                executionOrder);
        Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < executionOrder.size(); index++) {
            positions.put(executionOrder.get(index), index);
        }
        assertTrue(positions.get("compile") < positions.get("package"));
        assertTrue(positions.get("compile") < positions.get("integration-test"));
        assertTrue(positions.get("lint") < positions.get("publish"));
        assertTrue(positions.get("package") < positions.get("publish"));
        assertTrue(positions.get("integration-test") < positions.get("publish"));
        assertEquals(
                List.of("compile", "integration-test", "lint", "package"),
                pipeline.getAncestors("publish").stream().sorted().toList());
    }
}
