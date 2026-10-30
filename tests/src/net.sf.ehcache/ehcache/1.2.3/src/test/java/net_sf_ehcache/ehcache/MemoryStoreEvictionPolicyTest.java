/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertSame;

@Timeout(120)
public class MemoryStoreEvictionPolicyTest {
    @Test
    void resolvesPolicyNamesAndDefaultsUnknownNamesToLru() {
        assertSame(MemoryStoreEvictionPolicy.FIFO, MemoryStoreEvictionPolicy.fromString("fifo"));
        assertSame(MemoryStoreEvictionPolicy.LFU, MemoryStoreEvictionPolicy.fromString("lfu"));
        assertSame(MemoryStoreEvictionPolicy.LRU, MemoryStoreEvictionPolicy.fromString("lru"));
        assertSame(MemoryStoreEvictionPolicy.LRU, MemoryStoreEvictionPolicy.fromString("unspecified"));
    }
}
