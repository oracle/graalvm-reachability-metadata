/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.JNDIManualRMICacheManagerPeerProviderFactory;
import net.sf.ehcache.util.PropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class JNDIManualRMICacheManagerPeerProviderFactoryTest {
    @TempDir
    Path directory;

    @Test
    @ResourceLock("jndi-properties")
    void createsProviderWithConfiguredJndiUrls() throws Exception {
        try (JndiFixture fixture = new JndiFixture(directory)) {
            Properties properties = PropertyUtil.parseProperties("peerDiscovery=manual,stashContexts=true,"
                    + "stashRemoteCachePeers=true,jndiUrls=" + fixture.url);
            CacheManagerPeerProvider provider = new JNDIManualRMICacheManagerPeerProviderFactory()
                    .createCachePeerProvider(fixture.manager, properties);
            try {
                provider.init();
                List peers = provider.listRemoteCachePeers(fixture.cache);
                assertEquals(1, peers.size());
                assertEquals(fixture.cache.getGuid(), ((CachePeer) peers.get(0)).getGuid());
                assertEquals(1, provider.listRemoteCachePeers(fixture.cache).size());
            } finally {
                provider.dispose();
            }
        }
    }
}
