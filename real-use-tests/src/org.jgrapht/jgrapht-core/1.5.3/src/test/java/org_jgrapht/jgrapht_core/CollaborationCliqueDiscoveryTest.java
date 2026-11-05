/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.clique.PivotBronKerboschCliqueFinder;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's maximal-clique discovery subsystem for a collaboration service.
public class CollaborationCliqueDiscoveryTest {
    @Test
    @Timeout(60)
    public void findsTheLargestFullyConnectedCollaborationGroup() {
        Graph<String, DefaultEdge> collaborations = new SimpleGraph<>(DefaultEdge.class);
        for (String person : Set.of("alice", "bob", "carol", "dana", "erin")) {
            collaborations.addVertex(person);
        }
        connect(collaborations, "alice", "bob");
        connect(collaborations, "alice", "carol");
        connect(collaborations, "bob", "carol");
        connect(collaborations, "carol", "dana");
        connect(collaborations, "dana", "erin");

        PivotBronKerboschCliqueFinder<String, DefaultEdge> groups =
                new PivotBronKerboschCliqueFinder<>(collaborations);
        Iterator<Set<String>> largestGroups = groups.maximumIterator();

        assertTrue(largestGroups.hasNext());
        assertEquals(Set.of("alice", "bob", "carol"), largestGroups.next());
        assertFalse(largestGroups.hasNext());
    }

    private static void connect(Graph<String, DefaultEdge> collaborations, String first, String second) {
        collaborations.addEdge(first, second);
    }
}
