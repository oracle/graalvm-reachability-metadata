/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Timeout(120)
public class CacheManagerTest {
    @TempDir
    Path directory;

    @Test
    void managesIndependentNamedCaches() {
        CacheManager manager = TestSupport.manager(directory);
        try {
            manager.addCache("orders");
            manager.addCache("users");
            manager.getCache("orders").put(new Element("id", "order"));
            manager.getCache("users").put(new Element("id", "user"));
            assertEquals("order", manager.getCache("orders").get("id").getObjectValue());
            assertEquals("user", manager.getCache("users").get("id").getObjectValue());
            manager.removeCache("orders");
            assertFalse(manager.cacheExists("orders"));
            manager.clearAll();
            assertEquals(0, manager.getCache("users").getSize());
        } finally {
            manager.shutdown();
        }
        assertEquals(Status.STATUS_SHUTDOWN, manager.getStatus());
    }
}
