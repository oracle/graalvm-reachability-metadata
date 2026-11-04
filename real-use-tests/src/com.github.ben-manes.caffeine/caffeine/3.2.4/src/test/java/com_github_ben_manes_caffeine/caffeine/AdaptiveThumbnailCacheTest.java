/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Caffeine's runtime eviction-policy subsystem for a thumbnail service.
 * The service reduces its cache capacity and observes the bounded cache after a memory-pressure event.
 */
public class AdaptiveThumbnailCacheTest {
    @Test
    @Timeout(60)
    public void adaptsThumbnailCapacityAtRuntime() {
        Cache<String, Thumbnail> thumbnails = Caffeine.newBuilder()
                .maximumSize(3)
                .build();
        Thumbnail home = new Thumbnail("home", 320);
        Thumbnail search = new Thumbnail("search", 320);
        Thumbnail account = new Thumbnail("account", 320);
        thumbnails.put(home.name(), home);
        thumbnails.put(search.name(), search);
        thumbnails.put(account.name(), account);

        Policy.Eviction<String, Thumbnail> eviction = thumbnails.policy()
                .eviction()
                .orElseThrow();
        Map<String, Thumbnail> coldest = eviction.coldest(3);
        eviction.setMaximum(1);
        thumbnails.cleanUp();

        assertEquals(3, coldest.size());
        assertEquals(1, eviction.getMaximum());
        assertEquals(1, thumbnails.estimatedSize());
    }

    private record Thumbnail(String name, int width) {
    }
}
