/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.cookie.CookieSpecBase;
import org.apache.commons.httpclient.cookie.MalformedCookieException;
import org.junit.jupiter.api.Test;

public class CookieSpecBaseTest {
    @Test
    void legacyClassHelperResolvesCookieSpecInterface() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                CookieSpecBase.class, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(CookieSpecBase.class, "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> cookieSpecClass = (Class<?>) classHelper.invoke(
                "org.apache.commons.httpclient.cookie.CookieSpec");

        assertThat(cookieSpecClass.getName())
                .isEqualTo("org.apache.commons.httpclient.cookie.CookieSpec");
    }

    @Test
    void parsesValidatesMatchesAndFormatsBrowserCompatibleCookie() throws Exception {
        CookieSpecBase cookieSpec = new CookieSpecBase();

        Cookie[] cookies = cookieSpec.parse("Example.COM", 80, "/app/page", false,
                "session=abc123; Path=/app; Domain=.example.com; Secure; Comment=login");

        assertThat(cookies).hasSize(1);
        Cookie cookie = cookies[0];
        assertThat(cookie.getName()).isEqualTo("session");
        assertThat(cookie.getValue()).isEqualTo("abc123");
        assertThat(cookie.getDomain()).isEqualTo(".example.com");
        assertThat(cookie.getPath()).isEqualTo("/app");
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getComment()).isEqualTo("login");
        assertThat(cookie.isDomainAttributeSpecified()).isTrue();
        assertThat(cookie.isPathAttributeSpecified()).isTrue();

        cookieSpec.validate("example.com", 80, "/app/page", true, cookie);
        assertThat(cookieSpec.match("example.com", 80, "/app/page", true, cookie)).isTrue();
        assertThat(cookieSpec.match("example.com", 80, "/app/page", false, cookie)).isFalse();
        assertThat(cookieSpec.formatCookie(cookie)).isEqualTo("session=abc123");
        assertThat(cookieSpec.formatCookies(cookies)).isEqualTo("session=abc123");
        assertThat(cookieSpec.formatCookieHeader(cookie).getValue()).isEqualTo("session=abc123");
        assertThat(cookieSpec.formatCookieHeader(cookies).getName()).isEqualTo("Cookie");
    }

    @Test
    void parsesHeaderAndAppliesCookieAttributes() throws Exception {
        CookieSpecBase cookieSpec = new CookieSpecBase();
        Header header = new Header("Set-Cookie", "theme=light; Path=/; Domain=example.com");

        Cookie[] cookies = cookieSpec.parse("example.com", 80, "/index.html", false, header);

        assertThat(cookies).hasSize(1);
        assertThat(cookies[0].getName()).isEqualTo("theme");
        assertThat(cookies[0].getValue()).isEqualTo("light");
        assertThat(cookies[0].getPath()).isEqualTo("/");
        assertThat(cookies[0].getDomain()).isEqualTo("example.com");
        assertThat(cookieSpec.domainMatch("www.example.com", "example.com")).isTrue();
        assertThat(cookieSpec.pathMatch("/accounts/profile", "/accounts")).isTrue();
        assertThat(cookieSpec.pathMatch("/accounting", "/accounts")).isFalse();
    }

    @Test
    void parseAttributeRejectsInvalidDomainAndMaxAgeValues() {
        CookieSpecBase cookieSpec = new CookieSpecBase();
        Cookie cookie = new Cookie("example.com", "session", "abc123", "/", null, false);

        assertThatThrownBy(() -> cookieSpec.parseAttribute(
                new NameValuePair("domain", " "), cookie))
                .isInstanceOf(MalformedCookieException.class)
                .hasMessage("Blank value for domain attribute");
        assertThatThrownBy(() -> cookieSpec.parseAttribute(
                new NameValuePair("max-age", "later"), cookie))
                .isInstanceOf(MalformedCookieException.class)
                .hasMessageContaining("Invalid max-age attribute");
    }
}
