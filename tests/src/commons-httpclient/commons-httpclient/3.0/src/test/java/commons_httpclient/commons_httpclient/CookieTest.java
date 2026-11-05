/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.apache.commons.httpclient.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class CookieTest {
    @Test
    void normalizesDomainAndTracksExpirationAndIdentity() {
        Cookie cookie = new Cookie("EXAMPLE.TEST", "session", "first", "/app", new Date(20000L), true);
        assertEquals("example.test", cookie.getDomain());
        assertTrue(cookie.getSecure());
        assertFalse(cookie.isExpired(new Date(10000L)));
        assertTrue(cookie.isExpired(new Date(30000L)));
        Cookie replacement = new Cookie("example.test", "session", "second", "/app", null, false);
        assertEquals(cookie, replacement);
        assertTrue(cookie.toExternalForm().contains("session=first"));
    }
}
