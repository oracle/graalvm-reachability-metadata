/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.util.List;
import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.CachePeer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class RMICachePeerStubTest {
    @Test
    void accessesCacheThroughExportedRemoteStub() throws Exception {
        try (PeerFixture fixture = new PeerFixture()) {
            CachePeer remote = fixture.client;
            remote.put(new Element("remote-key", "remote-value"));
            assertEquals("remote-value", remote.getQuiet("remote-key").getObjectValue());
            assertEquals(List.of("remote-key"), remote.getKeys());
            assertEquals("replicated", remote.getName());
            assertEquals(fixture.cache.getGuid(), remote.getGuid());
            assertEquals("remote-value", ((Element) remote.getElements(List.of("remote-key")).get(0)).getObjectValue());
            assertTrue(remote.remove("remote-key"));
            assertEquals(0, fixture.cache.getSize());
        }
    }
}
