/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import org.eclipse.jetty.http.ComplianceViolation;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.CookieCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CookieCacheTest {

    @Test
    void convertsParsedHttpCookiesToApiCookieArray() {
        HttpFields.Mutable headers = HttpFields.build();
        headers.add(HttpHeader.COOKIE, "theme=light; session=abc");

        CookieCache cookieCache = new CookieCache();
        cookieCache.parseCookies(headers, ComplianceViolation.Listener.NOOP);

        String[] apiCookies = cookieCache.getApiCookies(
                String.class,
                cookie -> cookie.getName() + "=" + cookie.getValue());

        assertThat(apiCookies).containsExactly("theme=light", "session=abc");
    }
}
