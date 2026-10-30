/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import java.util.List;
import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.JNDIManualRMICacheManagerPeerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class JNDIManualRMICacheManagerPeerProviderTest {
    @TempDir
    Path directory;

    @Test
    @ResourceLock("jndi-properties")
    void resolvesPeerFromJndiAndUnregistersIt() throws Exception {
        try (JndiFixture fixture = new JndiFixture(directory)) {
            JNDIManualRMICacheManagerPeerProvider provider = new JNDIManualRMICacheManagerPeerProvider(false, false);
            try {
                provider.init();
                provider.registerPeer(fixture.url);
                List peers = provider.listRemoteCachePeers(fixture.cache);
                assertEquals(1, peers.size());
                CachePeer remote = (CachePeer) peers.get(0);
                remote.put(new Element("jndi", "value"));
                assertEquals("value", fixture.cache.get("jndi").getObjectValue());
                provider.unregisterPeer(fixture.url);
                assertTrue(provider.listRemoteCachePeers(fixture.cache).isEmpty());
            } finally {
                provider.dispose();
            }
        }
    }
}
