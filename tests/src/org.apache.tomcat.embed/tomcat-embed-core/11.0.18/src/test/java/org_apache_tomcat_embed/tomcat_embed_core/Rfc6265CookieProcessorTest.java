/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.http.Cookie;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.ServerCookies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Rfc6265CookieProcessorTest {

    @Test
    void parsesMultipleRequestCookies() {
        Rfc6265CookieProcessor processor = new Rfc6265CookieProcessor();
        MimeHeaders headers = new MimeHeaders();
        headers.addValue("Cookie").setString("theme=dark; session=abc123");
        ServerCookies cookies = new ServerCookies(2);

        processor.parseCookieHeader(headers, cookies);

        assertThat(cookies.getCookieCount()).isEqualTo(2);
        assertCookie(cookies.getCookie(0), "theme", "dark");
        assertCookie(cookies.getCookie(1), "session", "abc123");
    }

    @Test
    void generatesResponseCookieWithSecurityAttributes() {
        Rfc6265CookieProcessor processor = new Rfc6265CookieProcessor();
        processor.setSameSiteCookies("strict");
        processor.setPartitioned(true);
        Cookie cookie = new Cookie("session", "abc123");
        cookie.setPath("/app");
        cookie.setSecure(true);
        cookie.setHttpOnly(true);

        String header = processor.generateHeader(cookie, null);

        assertThat(header).startsWith("session=abc123").contains("Path=/app", "Secure", "HttpOnly", "SameSite=Strict",
                "Partitioned");
    }

    private static void assertCookie(ServerCookie cookie, String expectedName, String expectedValue) {
        assertThat(cookie.getName().toString()).isEqualTo(expectedName);
        assertThat(cookie.getValue().toString()).isEqualTo(expectedValue);
    }
}
