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

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.cookie.CookieSpecBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class CookieSpecBaseTest {
    @Test
    void parsesValidatesMatchesAndFormatsSecureCookie() throws Exception {
        CookieSpecBase spec = new CookieSpecBase();
        Cookie[] cookies = spec.parse("example.test", 443, "/app/index", true, "session=token; Path=/app; Secure");
        assertEquals(1, cookies.length);
        Cookie cookie = cookies[0];
        spec.validate("example.test", 443, "/app/index", true, cookie);
        assertTrue(spec.match("example.test", 443, "/app/next", true, cookie));
        assertFalse(spec.match("example.test", 80, "/app/next", false, cookie));
        assertFalse(spec.match("other.test", 443, "/app/next", true, cookie));
        assertEquals("session=token", spec.formatCookie(cookie));
    }
}
