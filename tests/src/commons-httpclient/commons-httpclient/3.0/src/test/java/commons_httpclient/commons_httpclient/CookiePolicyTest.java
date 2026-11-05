/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.cookie.CookieSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class CookiePolicyTest {
    @Test
    void resolvesAndUsesEveryBuiltInCookiePolicy() throws Exception {
        for (String policy : new String[] {CookiePolicy.DEFAULT, CookiePolicy.RFC_2109,
                CookiePolicy.BROWSER_COMPATIBILITY, CookiePolicy.NETSCAPE}) {
            CookieSpec spec = CookiePolicy.getCookieSpec(policy);
            Cookie[] cookies = spec.parse("www.example.com", 80, "/app/index", false, "session=token; Path=/app");
            assertEquals(1, cookies.length);
            spec.validate("www.example.com", 80, "/app/index", false, cookies[0]);
            assertTrue(spec.match("www.example.com", 80, "/app/next", false, cookies[0]));
            assertTrue(spec.formatCookie(cookies[0]).contains("session=token"));
        }
        assertEquals(0, CookiePolicy.getCookieSpec(CookiePolicy.IGNORE_COOKIES)
                .parse("www.example.com", 80, "/", false, "session=token").length);
    }
}
