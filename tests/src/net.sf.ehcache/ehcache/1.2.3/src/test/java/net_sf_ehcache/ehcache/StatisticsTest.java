/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Statistics;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class StatisticsTest {
    @Test
    void capturesHitsMissesAndResetsCounters() {
        Cache cache = TestSupport.cache("statistics", 10, MemoryStoreEvictionPolicy.LRU);
        try {
            cache.put(new Element("key", "value"));
            cache.get("key");
            cache.get("absent");
            Statistics statistics = cache.getStatistics();
            assertEquals(1, statistics.getCacheHits());
            assertEquals(1, statistics.getInMemoryHits());
            assertEquals(1, statistics.getCacheMisses());
            assertEquals(1, statistics.getObjectCount());
            assertEquals("statistics", statistics.getAssociatedCacheName());
            statistics.clearStatistics();
            assertEquals(0, cache.getStatistics().getCacheHits());
            assertEquals(0, cache.getStatistics().getCacheMisses());
        } finally {
            cache.dispose();
        }
    }
}
