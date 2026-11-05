/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.color.SaturationDegreeColoring;
import org.jgrapht.alg.interfaces.VertexColoringAlgorithm;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/// Exercises JGraphT's vertex-coloring subsystem for an exam scheduling service.
public class ExamSchedulingTest {
    @Test
    @Timeout(60)
    public void assignsConflictingExamsToDifferentTimeSlots() {
        Graph<String, DefaultEdge> conflicts = new DefaultUndirectedGraph<>(DefaultEdge.class);
        for (String exam : List.of("algebra", "biology", "chemistry", "database", "economics")) {
            conflicts.addVertex(exam);
        }
        addConflict(conflicts, "algebra", "biology");
        addConflict(conflicts, "biology", "chemistry");
        addConflict(conflicts, "chemistry", "algebra");
        addConflict(conflicts, "database", "algebra");
        addConflict(conflicts, "database", "biology");
        addConflict(conflicts, "economics", "biology");
        addConflict(conflicts, "economics", "chemistry");

        VertexColoringAlgorithm.Coloring<String> schedule =
                new SaturationDegreeColoring<>(conflicts).getColoring();

        assertEquals(3, schedule.getNumberColors());
        assertEquals(5, schedule.getColors().size());
        int[] slotSizes = schedule.getColorClasses().stream()
                .mapToInt(Set::size)
                .sorted()
                .toArray();
        assertArrayEquals(new int[] {1, 2, 2}, slotSizes);
        for (DefaultEdge conflict : conflicts.edgeSet()) {
            assertNotEquals(
                    schedule.getColors().get(conflicts.getEdgeSource(conflict)),
                    schedule.getColors().get(conflicts.getEdgeTarget(conflict)));
        }
        assertEquals(
                schedule.getColorClasses().stream()
                        .flatMap(Set::stream)
                        .sorted()
                        .toList(),
                List.of("algebra", "biology", "chemistry", "database", "economics"));
    }

    private static void addConflict(Graph<String, DefaultEdge> conflicts, String first, String second) {
        conflicts.addEdge(first, second);
    }
}
