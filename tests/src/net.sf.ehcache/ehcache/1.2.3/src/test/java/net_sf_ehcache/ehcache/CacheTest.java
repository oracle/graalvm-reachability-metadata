/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class CacheTest {
    @Test
    void storesUpdatesAndRemovesEntries() {
        Cache cache = TestSupport.cache("entries", 10, MemoryStoreEvictionPolicy.LRU);
        try {
            cache.put(new Element("order", "created"));
            assertEquals("created", cache.get("order").getObjectValue());
            cache.put(new Element("order", "paid"));
            assertEquals("paid", cache.get("order").getObjectValue());
            assertEquals(1, cache.getSize());
            assertTrue(cache.remove("order"));
            assertNull(cache.get("order"));
        } finally {
            cache.dispose();
        }
    }
}
