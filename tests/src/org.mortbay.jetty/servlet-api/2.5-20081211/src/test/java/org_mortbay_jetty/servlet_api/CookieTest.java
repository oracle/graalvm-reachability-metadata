/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

public class CookieTest {
    @Test
    void constructorInitializesCookieState() {
        final Cookie cookie = new Cookie("session", "alpha");

        assertThat(cookie.getName()).isEqualTo("session");
        assertThat(cookie.getValue()).isEqualTo("alpha");
        assertThat(cookie.getMaxAge()).isEqualTo(-1);
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getVersion()).isZero();
    }

    @Test
    void settersUpdateCookieAttributes() {
        final Cookie cookie = new Cookie("preference", "compact");

        cookie.setComment("stores display preference");
        cookie.setDomain("Example.COM");
        cookie.setMaxAge(120);
        cookie.setPath("/account");
        cookie.setSecure(true);
        cookie.setValue("expanded");
        cookie.setVersion(1);

        assertThat(cookie.getComment()).isEqualTo("stores display preference");
        assertThat(cookie.getDomain()).isEqualTo("example.com");
        assertThat(cookie.getMaxAge()).isEqualTo(120);
        assertThat(cookie.getPath()).isEqualTo("/account");
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getValue()).isEqualTo("expanded");
        assertThat(cookie.getVersion()).isEqualTo(1);
    }

    @Test
    void constructorRejectsReservedCookieNamesWithLocalizedMessage() {
        assertThatThrownBy(() -> new Cookie("Domain", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cookie name \"Domain\" is a reserved token");
    }
}
