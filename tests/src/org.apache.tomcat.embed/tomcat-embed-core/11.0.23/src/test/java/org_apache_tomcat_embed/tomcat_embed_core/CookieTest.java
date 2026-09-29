/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CookieTest {

    @Test
    void retainsConfiguredCookieProperties() {
        Cookie cookie = new Cookie("session", "abc123");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/application");

        assertThat(cookie.getName()).isEqualTo("session");
        assertThat(cookie.getValue()).isEqualTo("abc123");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/application");
    }
}
