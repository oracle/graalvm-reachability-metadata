/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JGraphT's PageRank scoring subsystem for a content recommendation service.
public class ContentRankingTest {
    @Test
    @Timeout(60)
    public void ranksContentByTheLinksItReceives() {
        Graph<String, DefaultEdge> content =
                new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String page : List.of("home", "java", "graphs", "security", "archive")) {
            content.addVertex(page);
        }
        content.addEdge("home", "java");
        content.addEdge("home", "graphs");
        content.addEdge("java", "graphs");
        content.addEdge("graphs", "java");
        content.addEdge("security", "java");
        content.addEdge("security", "archive");
        content.addEdge("archive", "java");

        PageRank<String, DefaultEdge> ranking = new PageRank<>(content, 0.85, 100, 1e-9);
        Map<String, Double> scores = ranking.getScores();

        assertEquals(5, scores.size());
        assertEquals(scores.get("java"), ranking.getVertexScore("java"));
        assertTrue(scores.get("java") > scores.get("graphs"));
        assertTrue(scores.get("graphs") > scores.get("archive"));
        assertTrue(scores.get("archive") > scores.get("home"));
        assertTrue(scores.get("home") >= scores.get("security"));
    }
}
