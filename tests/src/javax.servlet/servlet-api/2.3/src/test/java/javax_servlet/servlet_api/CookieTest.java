/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

public class CookieTest {
    @Test
    void constructorInitializesCookieResourceBundleAndDefaultState() {
        final Cookie cookie = new Cookie("sessionId", "abc123");

        assertThat(cookie.getName()).isEqualTo("sessionId");
        assertThat(cookie.getValue()).isEqualTo("abc123");
        assertThat(cookie.getComment()).isNull();
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getMaxAge()).isEqualTo(-1);
        assertThat(cookie.getPath()).isNull();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getVersion()).isZero();
    }

    @Test
    void constructorUsesCookieResourceBundleForReservedTokenMessages() {
        assertThatThrownBy(() -> new Cookie("Comment", "ignored"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cookie name Comment is a reserved token");
    }

    @Test
    void accessorsMutateCookieAttributes() {
        final Cookie cookie = new Cookie("theme", "light");

        cookie.setValue("dark");
        cookie.setComment("user preference");
        cookie.setDomain("Example.COM");
        cookie.setMaxAge(3600);
        cookie.setPath("/settings");
        cookie.setSecure(true);
        cookie.setVersion(1);

        assertThat(cookie.getValue()).isEqualTo("dark");
        assertThat(cookie.getComment()).isEqualTo("user preference");
        assertThat(cookie.getDomain()).isEqualTo("example.com");
        assertThat(cookie.getMaxAge()).isEqualTo(3600);
        assertThat(cookie.getPath()).isEqualTo("/settings");
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getVersion()).isEqualTo(1);
    }

    @Test
    void cloneCopiesCookieStateWithoutSharingIdentity() {
        final Cookie cookie = new Cookie("cart", "full");
        cookie.setPath("/shop");
        cookie.setSecure(true);

        final Cookie clone = (Cookie) cookie.clone();

        assertThat(clone).isNotSameAs(cookie);
        assertThat(clone.getName()).isEqualTo(cookie.getName());
        assertThat(clone.getValue()).isEqualTo(cookie.getValue());
        assertThat(clone.getPath()).isEqualTo(cookie.getPath());
        assertThat(clone.getSecure()).isEqualTo(cookie.getSecure());
    }
}
