/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.constructs.blocking.UpdatingCacheEntryFactory;
import net.sf.ehcache.constructs.blocking.UpdatingSelfPopulatingCache;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@Timeout(120)
public class UpdatingSelfPopulatingCacheTest {
    @Test
    void updatesExistingMutableValueOnEachAccess() {
        Cache backing = TestSupport.cache("updating", 10, MemoryStoreEvictionPolicy.LRU);
        UpdatingCacheEntryFactory factory = new UpdatingCacheEntryFactory() {
            @Override
            public Object createEntry(Object key) {
                return new StringBuilder(key.toString());
            }

            @Override
            public void updateEntryValue(Object key, Object value) {
                ((StringBuilder) value).append("-updated");
            }
        };
        UpdatingSelfPopulatingCache cache = new UpdatingSelfPopulatingCache(backing, factory);
        cache.setTimeoutMillis(10000);
        try {
            Object value = cache.get("key").getObjectValue();
            assertEquals("key", value.toString());
            assertSame(value, cache.get("key").getObjectValue());
            assertEquals("key-updated", value.toString());
        } finally {
            cache.dispose();
        }
    }
}
