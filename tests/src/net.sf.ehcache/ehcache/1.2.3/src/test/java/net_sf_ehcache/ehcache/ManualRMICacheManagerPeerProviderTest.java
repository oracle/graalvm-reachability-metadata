/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.util.List;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.ManualRMICacheManagerPeerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ManualRMICacheManagerPeerProviderTest {
    @Test
    void discoversRegisteredPeerForMatchingCacheAndUnregistersIt() throws Exception {
        try (PeerFixture fixture = new PeerFixture()) {
            ManualRMICacheManagerPeerProvider provider = new ManualRMICacheManagerPeerProvider();
            try {
                provider.init();
                provider.registerPeer(fixture.url);
                List peers = provider.listRemoteCachePeers(fixture.cache);
                assertEquals(1, peers.size());
                assertEquals("replicated", ((CachePeer) peers.get(0)).getName());
                provider.unregisterPeer(fixture.url);
                assertTrue(provider.listRemoteCachePeers(fixture.cache).isEmpty());
            } finally {
                provider.dispose();
            }
        }
    }
}
