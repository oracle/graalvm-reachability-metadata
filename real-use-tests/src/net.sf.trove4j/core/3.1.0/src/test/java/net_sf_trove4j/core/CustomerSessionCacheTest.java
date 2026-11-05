/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.THashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises Trove's object-object map subsystem to maintain a customer session cache.
public class CustomerSessionCacheTest {
    @Test
    @Timeout(60)
    public void keepsExistingSessionsAndRefreshesCachedValues() {
        THashMap<String, Session> sessionsByToken = new THashMap<>();
        Session firstSession = new Session("customer-17", 1);
        Session replacementSession = new Session("customer-17", 2);
        sessionsByToken.put("token-a", firstSession);
        sessionsByToken.put("token-b", new Session("customer-23", 1));

        Session retainedSession = sessionsByToken.putIfAbsent("token-a", replacementSession);
        sessionsByToken.putIfAbsent("token-c", new Session("customer-41", 1));
        sessionsByToken.transformValues(session -> new Session(session.customerId(), session.version() + 1));

        assertSame(firstSession, retainedSession);
        assertEquals(3, sessionsByToken.size());
        assertEquals(new Session("customer-17", 2), sessionsByToken.get("token-a"));
        assertEquals(new Session("customer-23", 2), sessionsByToken.get("token-b"));
        assertEquals(new Session("customer-41", 2), sessionsByToken.get("token-c"));
        assertEquals(List.of("token-a", "token-b", "token-c"),
                sessionsByToken.keySet().stream().sorted().toList());
    }

    private record Session(String customerId, int version) {
    }
}
