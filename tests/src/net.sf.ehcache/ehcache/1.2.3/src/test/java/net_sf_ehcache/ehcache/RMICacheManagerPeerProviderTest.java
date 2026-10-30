/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.ManualRMICacheManagerPeerProvider;
import net.sf.ehcache.distribution.RMICacheManagerPeerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class RMICacheManagerPeerProviderTest {
    @Test
    void looksUpRemotePeerByRmiUrl() throws Exception {
        try (PeerFixture fixture = new PeerFixture()) {
            RMICacheManagerPeerProvider provider = new ManualRMICacheManagerPeerProvider();
            try {
                CachePeer remote = provider.lookupRemoteCachePeer(fixture.url);
                remote.put(new Element("lookup", "value"));
                assertEquals("value", fixture.cache.get("lookup").getObjectValue());
                assertEquals(fixture.url, remote.getUrl());
            } finally {
                provider.dispose();
            }
        }
    }
}
