/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.linkprediction.CommonNeighborsLinkPrediction;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's link-prediction subsystem for a community recommendation service.
public class CommunityRecommendationTest {
    @Test
    @Timeout(60)
    public void ranksSuggestedConnectionsBySharedCommunities() {
        Graph<String, DefaultEdge> community = new SimpleGraph<>(DefaultEdge.class);
        for (String member : new String[] {"alice", "bob", "carol", "dave", "erin"}) {
            community.addVertex(member);
        }
        connect(community, "alice", "bob");
        connect(community, "alice", "carol");
        connect(community, "bob", "carol");
        connect(community, "bob", "dave");
        connect(community, "carol", "dave");
        connect(community, "bob", "erin");

        CommonNeighborsLinkPrediction<String, DefaultEdge> recommender =
                new CommonNeighborsLinkPrediction<>(community);

        double aliceDaveScore = recommender.predict("alice", "dave");
        double aliceErinScore = recommender.predict("alice", "erin");

        assertEquals(2.0, aliceDaveScore);
        assertEquals(1.0, aliceErinScore);
        assertTrue(aliceDaveScore > aliceErinScore);
    }

    private static void connect(Graph<String, DefaultEdge> community, String first, String second) {
        community.addEdge(first, second);
    }
}
