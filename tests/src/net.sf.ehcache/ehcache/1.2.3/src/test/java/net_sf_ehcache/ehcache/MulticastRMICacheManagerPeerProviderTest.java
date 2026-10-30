/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.MulticastRMICacheManagerPeerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class MulticastRMICacheManagerPeerProviderTest {
    @TempDir
    Path directory;

    @Test
    void tracksLivePeerAndRemovesExplicitlyUnregisteredPeer() throws Exception {
        try (PeerFixture remote = new PeerFixture()) {
            CacheManager manager = TestSupport.manager(directory);
            try {
                MulticastRMICacheManagerPeerProvider provider = new MulticastRMICacheManagerPeerProvider(
                        manager, InetAddress.getByName("230.0.0.77"), 4446);
                provider.registerPeer(remote.url);
                List peers = provider.listRemoteCachePeers(remote.cache);
                assertEquals(1, peers.size());
                assertEquals(remote.url, ((CachePeer) peers.get(0)).getUrl());
                provider.unregisterPeer(remote.url);
                assertTrue(provider.listRemoteCachePeers(remote.cache).isEmpty());
            } finally {
                manager.shutdown();
            }
        }
    }
}
