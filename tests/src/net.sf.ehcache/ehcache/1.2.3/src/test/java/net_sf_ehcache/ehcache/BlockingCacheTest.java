/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.BlockingCache;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class BlockingCacheTest {
    @Test
    void fillsAndReleasesMissingEntryLock() {
        Cache backing = TestSupport.cache("blocking", 10, MemoryStoreEvictionPolicy.LRU);
        BlockingCache cache = new BlockingCache(backing);
        cache.setTimeoutMillis(10000);
        try {
            assertNull(cache.get("key"));
            cache.put(new Element("key", "filled"));
            assertEquals("filled", cache.get("key").getObjectValue());
            assertTrue(cache.remove("key"));
            assertNull(cache.get("key"));
            cache.put(new Element("key", "refilled"));
            assertEquals("refilled", cache.get("key").getObjectValue());
        } finally {
            cache.dispose();
        }
    }
}
