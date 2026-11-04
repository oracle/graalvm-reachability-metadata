/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Caffeine's statistics-recording subsystem for an inventory service.
 * The service observes cache hits and misses while serving product availability requests.
 */
public class CacheStatisticsTest {
    @Test
    @Timeout(60)
    public void reportsHitAndMissCountsForInventoryRequests() {
        Cache<String, InventoryItem> inventory = Caffeine.newBuilder()
                .maximumSize(100)
                .recordStats()
                .build();
        InventoryItem boots = new InventoryItem("sku-100", 12);
        inventory.put(boots.sku(), boots);

        assertEquals(boots, inventory.getIfPresent("sku-100"));
        assertNull(inventory.getIfPresent("sku-404"));
        InventoryItem jacket = inventory.get("sku-200", sku -> new InventoryItem(sku, 7));
        assertNotNull(jacket);
        assertEquals(jacket, inventory.getIfPresent("sku-200"));

        CacheStats statistics = inventory.stats();
        assertEquals(4, statistics.requestCount());
        assertEquals(2, statistics.hitCount());
        assertEquals(2, statistics.missCount());
        assertEquals(0, statistics.evictionCount());
        assertEquals(1, statistics.loadSuccessCount());
        assertEquals(0, statistics.loadFailureCount());
        assertEquals(0.5, statistics.hitRate());
    }

    private record InventoryItem(String sku, int quantity) {
    }
}
