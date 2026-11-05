/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.cache.Cache;
import io.trino.jdbc.$internal.guava.cache.CacheBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CacheStriped64Anonymous1Test {
    @Test
    void recordsCacheHitsAndMisses() {
        Cache<String, String> cache = CacheBuilder.newBuilder().recordStats().build();
        assertNull(cache.getIfPresent("entry"));
        cache.put("entry", "value");
        assertEquals("value", cache.getIfPresent("entry"));
        assertEquals(1, cache.stats().hitCount());
        assertEquals(1, cache.stats().missCount());
        cache.invalidateAll();
    }
}
