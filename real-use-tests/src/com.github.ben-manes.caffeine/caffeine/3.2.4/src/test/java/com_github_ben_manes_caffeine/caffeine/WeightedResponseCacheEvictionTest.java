/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Caffeine's weighted-eviction subsystem for a search response cache.
 * Responses that exceed the byte budget are not retained while compact responses remain available.
 */
public class WeightedResponseCacheEvictionTest {
    @Test
    @Timeout(60)
    public void rejectsResponsesThatExceedTheCacheWeightBudget() {
        Cache<String, SearchResponse> responses = Caffeine.newBuilder()
                .maximumWeight(10)
                .weigher((String query, SearchResponse response) -> response.weight())
                .build();
        SearchResponse compact = new SearchResponse("coffee", 4);
        SearchResponse oversized = new SearchResponse("long-running-query", 11);

        responses.put(compact.query(), compact);
        responses.put(oversized.query(), oversized);
        responses.cleanUp();

        assertEquals(compact, responses.getIfPresent(compact.query()));
        assertNull(responses.getIfPresent(oversized.query()));
        assertEquals(1, responses.estimatedSize());
    }

    private record SearchResponse(String query, int weight) {
    }
}
