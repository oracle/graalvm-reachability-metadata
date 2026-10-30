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
import net.sf.ehcache.distribution.RMIBootstrapCacheLoader;
import net.sf.ehcache.distribution.RMIBootstrapCacheLoaderFactory;
import net.sf.ehcache.util.PropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Timeout(120)
public class RMIBootstrapCacheLoaderFactoryTest {
    @TempDir
    Path directory;

    @Test
    void bootstrapsEntriesFromDiscoveredRemotePeer() throws Exception {
        try (PeerFixture fixture = new PeerFixture()) {
            fixture.cache.put(new Element("first", "one"));
            fixture.cache.put(new Element("second", "two"));
            CacheManager manager = TestSupport.replicatingManager(directory, fixture.url);
            try {
                RMIBootstrapCacheLoader loader = (RMIBootstrapCacheLoader) new RMIBootstrapCacheLoaderFactory()
                    .createBootstrapCacheLoader(PropertyUtil.parseProperties("bootstrapAsynchronously=false,maximumChunkSizeBytes=10000"));
                Cache target = manager.getCache("replicated");
                loader.load(target);
                assertFalse(loader.isAsynchronous());
                assertEquals(10000, loader.getMaximumChunkSizeBytes());
                assertEquals(2, target.getSize());
                assertEquals("one", target.get("first").getObjectValue());
                assertEquals("two", target.get("second").getObjectValue());
            } finally {
                manager.shutdown();
            }
        }
    }
}
