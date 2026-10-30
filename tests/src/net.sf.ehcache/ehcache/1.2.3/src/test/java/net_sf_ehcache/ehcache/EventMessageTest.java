/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.util.ArrayList;
import java.util.List;
import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.EventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(120)
public class EventMessageTest {
    @Test
    void sendsSerializedEventsToRemoteCache() throws Exception {
        try (PeerFixture fixture = new PeerFixture()) {
            Element payload = new Element("event-key", new ArrayList<>(List.of("payload")));
            fixture.client.send(List.of(new EventMessage(EventMessage.PUT, "event-key", payload)));
            assertEquals(List.of("payload"), fixture.cache.get("event-key").getObjectValue());
            fixture.client.send(List.of(new EventMessage(EventMessage.REMOVE, "event-key", null)));
            assertNull(fixture.cache.get("event-key"));
            fixture.client.send(List.of(new EventMessage(EventMessage.PUT, "event-key", payload),
                    new EventMessage(EventMessage.REMOVE_ALL, null, null)));
            assertEquals(0, fixture.cache.getSize());
        }
    }
}
