/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CookieTest {
    @Test
    void retainsCookiePolicyWhenCloned() {
        Cookie cookie = new Cookie("session", "value");
        cookie.setDomain("EXAMPLE.COM");
        cookie.setPath("/shop");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        Cookie copy = (Cookie) cookie.clone();
        copy.setValue("updated");
        assertEquals("example.com", copy.getDomain());
        assertEquals("/shop", copy.getPath());
        assertTrue(copy.isHttpOnly());
        assertTrue(copy.getSecure());
        assertEquals("value", cookie.getValue());
        assertFalse(copy.getValue().equals(cookie.getValue()));
    }
}
