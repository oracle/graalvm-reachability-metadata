/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.cache.Cache;
import com.couchbase.client.core.deps.com.google.common.cache.CacheBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CacheStriped64Anonymous1Test {
    @Test
    void exercisesPublicApi() throws Exception {
        Cache<String, String> cache = CacheBuilder.newBuilder().recordStats().build();
        assertNull(cache.getIfPresent("missing"));
        cache.put("key", "value");
        assertEquals("value", cache.getIfPresent("key"));
        assertEquals(1, cache.stats().hitCount());
        assertEquals(1, cache.stats().missCount());
    }
}
