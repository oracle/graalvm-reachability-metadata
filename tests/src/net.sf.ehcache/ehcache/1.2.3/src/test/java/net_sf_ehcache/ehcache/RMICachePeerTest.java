/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.util.List;
import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.EventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class RMICachePeerTest {
    @Test
    void appliesBatchEventsAndExposesPeerIdentity() throws Exception {
        try (PeerFixture fixture = new PeerFixture()) {
            fixture.peer.send(List.of(new EventMessage(EventMessage.PUT, "batch", new Element("batch", "value"))));
            assertEquals("value", fixture.cache.get("batch").getObjectValue());
            assertEquals(fixture.url, fixture.peer.getUrlBase() + "/" + fixture.peer.getName());
            fixture.peer.removeAll();
            assertEquals(0, fixture.cache.getSize());
        }
    }
}
