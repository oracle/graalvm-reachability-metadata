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
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.MulticastRMICacheManagerPeerProvider;
import net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory;
import net.sf.ehcache.util.PropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class MulticastKeepaliveHeartbeatReceiverTest {
    @TempDir
    Path directory;

    @Test
    void configuresDiscoveryAndResolvesRegisteredPeerNotifications() throws Exception {
        try (PeerFixture remote = new PeerFixture()) {
            CacheManager manager = TestSupport.manager(directory);
            try {
                MulticastRMICacheManagerPeerProvider provider = (MulticastRMICacheManagerPeerProvider)
                        new RMICacheManagerPeerProviderFactory().createCachePeerProvider(manager,
                                PropertyUtil.parseProperties("peerDiscovery=automatic,multicastGroupAddress=230.0.0.77,multicastGroupPort=4446"));
                provider.registerPeer(remote.url);
                CachePeer discovered = (CachePeer) provider.listRemoteCachePeers(remote.cache).get(0);
                assertEquals(remote.cache.getGuid(), discovered.getGuid());
                discovered.put(new Element("notification", "discovered"));
                assertEquals("discovered", remote.cache.get("notification").getObjectValue());
                manager.addCache("unrelated");
                Cache unrelated = manager.getCache("unrelated");
                assertEquals(0, provider.listRemoteCachePeers(unrelated).size());
            } finally {
                manager.shutdown();
            }
        }
    }
}
