/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.util.concurrent.atomic.AtomicInteger;
import net.sf.ehcache.Cache;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class SelfPopulatingCacheTest {
    @Test
    void loadsOnceAndRefreshesCachedValues() {
        Cache backing = TestSupport.cache("loading", 10, MemoryStoreEvictionPolicy.LRU);
        AtomicInteger loads = new AtomicInteger();
        SelfPopulatingCache cache = new SelfPopulatingCache(backing, key -> key + "-" + loads.incrementAndGet());
        cache.setTimeoutMillis(10000);
        try {
            assertEquals("key-1", cache.get("key").getObjectValue());
            assertEquals("key-1", cache.get("key").getObjectValue());
            assertEquals(1, loads.get());
            cache.refresh();
            assertEquals("key-2", cache.get("key").getObjectValue());
            assertEquals(2, loads.get());
        } finally {
            cache.dispose();
        }
    }
}
