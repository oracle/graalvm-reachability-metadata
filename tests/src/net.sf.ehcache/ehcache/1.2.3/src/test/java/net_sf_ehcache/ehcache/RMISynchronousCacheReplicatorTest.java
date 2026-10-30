/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.RMISynchronousCacheReplicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class RMISynchronousCacheReplicatorTest {
    @TempDir
    Path directory;

    @Test
    void replicatesPutUpdateRemoveAndClearNotifications() throws Exception {
        try (PeerFixture fixture = new PeerFixture()) {
            CacheManager manager = TestSupport.replicatingManager(directory, fixture.url);
            RMISynchronousCacheReplicator replicator = new RMISynchronousCacheReplicator(true, true, true, true);
            Cache cache = manager.getCache("replicated");
            cache.getCacheEventNotificationService().registerListener(replicator);
            try {
                cache.put(new Element("key", "first"));
                TestSupport.await(() -> fixture.cache.getQuiet("key") != null);
                assertEquals("first", fixture.cache.get("key").getObjectValue());
                cache.put(new Element("key", "updated"));
                TestSupport.await(() -> "updated".equals(fixture.cache.get("key").getObjectValue()));
                cache.remove("key");
                TestSupport.await(() -> fixture.cache.getQuiet("key") == null);
                cache.put(new Element("another", "value"));
                TestSupport.await(() -> fixture.cache.getQuiet("another") != null);
                cache.removeAll();
                TestSupport.await(() -> fixture.cache.getSize() == 0);
                assertTrue(replicator.alive());
            } finally {
                manager.shutdown();
            }
            assertTrue(replicator.notAlive());
        }
    }
}
