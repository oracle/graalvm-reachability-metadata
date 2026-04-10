/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.javax_servlet_api;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieTest {
    @Test
    void constructingCookieLoadsHttpCookieResources() {
        Cookie cookie = new Cookie("sessionId", "abc123");

        cookie.setPath("/app");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge(60);

        assertEquals("sessionId", cookie.getName());
        assertEquals("abc123", cookie.getValue());
        assertEquals("/app", cookie.getPath());
        assertEquals(60, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
    }

    @Test
    void blankCookieNameUsesResourceBackedValidationMessage() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new Cookie("", "value"));

        assertEquals("Cookie name must not be null or empty", exception.getMessage());
    }
}
