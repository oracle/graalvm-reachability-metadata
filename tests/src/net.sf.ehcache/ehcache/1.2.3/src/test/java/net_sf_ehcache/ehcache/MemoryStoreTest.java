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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class MemoryStoreTest {
    @Test
    void measuresMemoryAndClearsStoredEntries() {
        Cache cache = TestSupport.cache("memory", 10, MemoryStoreEvictionPolicy.LRU);
        try {
            cache.put(new Element("first", "one"));
            long firstSize = cache.calculateInMemorySize();
            cache.put(new Element("second", "two"));
            assertTrue(firstSize > 0);
            assertTrue(cache.calculateInMemorySize() > firstSize);
            assertEquals(2, cache.getMemoryStoreSize());
            cache.removeAll();
            assertEquals(0, cache.getMemoryStoreSize());
            assertEquals(0, cache.calculateInMemorySize());
        } finally {
            cache.dispose();
        }
    }
}
