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
    void storesAndClonesCookieAttributes() {
        Cookie cookie = new Cookie("session", "abc123");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/application");

        Cookie copy = (Cookie) cookie.clone();

        assertThat(copy.getName()).isEqualTo("session");
        assertThat(copy.getValue()).isEqualTo("abc123");
        assertThat(copy.isHttpOnly()).isTrue();
        assertThat(copy.getSecure()).isTrue();
        assertThat(copy.getPath()).isEqualTo("/application");
    }
}
