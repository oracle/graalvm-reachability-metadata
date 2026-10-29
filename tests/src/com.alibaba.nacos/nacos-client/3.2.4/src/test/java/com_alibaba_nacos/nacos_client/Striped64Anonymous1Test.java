/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.cache.Cache;
import com.alibaba.nacos.shaded.com.google.common.cache.CacheBuilder;
import com.alibaba.nacos.shaded.com.google.common.cache.CacheStats;
import org.junit.jupiter.api.Test;

public class Striped64Anonymous1Test {
    @Test
    void accumulatesCacheHitMissAndLoadStatistics() throws Exception {
        Cache<String, String> cache = CacheBuilder.newBuilder().recordStats().build();
        assertEquals("config", cache.get("key", () -> "config"));
        assertEquals("config", cache.getIfPresent("key"));
        CacheStats stats = cache.stats();
        assertEquals(1, stats.hitCount());
        assertEquals(1, stats.missCount());
        assertEquals(1, stats.loadSuccessCount());
    }
}
