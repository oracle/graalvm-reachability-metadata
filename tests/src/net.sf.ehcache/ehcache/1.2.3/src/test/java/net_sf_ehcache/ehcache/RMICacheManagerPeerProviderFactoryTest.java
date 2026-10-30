/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import java.util.Properties;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory;
import net.sf.ehcache.util.PropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class RMICacheManagerPeerProviderFactoryTest {
    @TempDir
    Path directory;

    @Test
    void configuresManualPeerDiscoveryFromProperties() throws Exception {
        try (PeerFixture fixture = new PeerFixture()) {
            CacheManager manager = TestSupport.manager(directory);
            Properties properties = PropertyUtil.parseProperties("peerDiscovery=manual,rmiUrls=" + fixture.url);
            CacheManagerPeerProvider provider = new RMICacheManagerPeerProviderFactory().createCachePeerProvider(manager, properties);
            try {
                provider.init();
                assertEquals(1, provider.listRemoteCachePeers(fixture.cache).size());
                assertEquals(0, provider.getTimeForClusterToForm());
            } finally {
                provider.dispose();
                manager.shutdown();
            }
        }
    }
}
