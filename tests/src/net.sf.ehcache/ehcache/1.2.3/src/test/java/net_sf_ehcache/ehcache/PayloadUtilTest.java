/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory;
import net.sf.ehcache.util.PropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class PayloadUtilTest {
    @TempDir
    Path directory;

    @Test
    void parsesDelimitedPeerUrlsThroughTheProviderFactory() throws Exception {
        try (PeerFixture first = new PeerFixture(); PeerFixture second = new PeerFixture()) {
            CacheManager manager = TestSupport.manager(directory);
            CacheManagerPeerProvider provider = new RMICacheManagerPeerProviderFactory().createCachePeerProvider(manager,
                    PropertyUtil.parseProperties("peerDiscovery=manual,rmiUrls=" + first.url + " | " + second.url));
            try {
                provider.init();
                List peers = provider.listRemoteCachePeers(first.cache);
                Set<String> discovered = new HashSet<>();
                for (Object peer : peers) {
                    discovered.add(((CachePeer) peer).getUrl());
                }
                assertEquals(Set.of(first.url, second.url), discovered);
            } finally {
                provider.dispose();
                manager.shutdown();
            }
        }
    }
}
