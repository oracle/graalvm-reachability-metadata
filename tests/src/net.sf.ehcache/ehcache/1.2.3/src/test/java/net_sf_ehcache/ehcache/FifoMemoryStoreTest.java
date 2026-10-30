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

@Timeout(120)
public class FifoMemoryStoreTest {
    @Test
    void evictsAccordingToConfiguredPolicy() {
        Cache cache = TestSupport.cache("eviction", 2, MemoryStoreEvictionPolicy.FIFO);
        try {
            cache.put(new Element("first", "one"));
            cache.put(new Element("second", "two"));
            cache.get("first");
            cache.get("first");
            cache.put(new Element("third", "three"));
            assertNull(cache.get("first"));
            assertEquals("three", cache.get("third").getObjectValue());
            assertEquals(2, cache.getSize());
        } finally {
            cache.dispose();
        }
    }
}
