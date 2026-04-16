/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

class CookieTests {
    @Test
    void constructorRejectsReservedTokenNameWithLocalizedMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cookie("Path", "value"))
                .withMessage("Cookie name \"Path\" is a reserved token");
    }

    @Test
    void cookieRetainsConfiguredStateAcrossClone() {
        final Cookie cookie = new Cookie("sessionId", "initial");
        cookie.setComment("Tracks the logged in session");
        cookie.setDomain("Example.COM");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(60);
        cookie.setPath("/catalog");
        cookie.setSecure(true);
        cookie.setValue("updated");
        cookie.setVersion(1);

        final Cookie clonedCookie = (Cookie) cookie.clone();

        assertThat(clonedCookie).isNotSameAs(cookie);
        assertThat(clonedCookie.getName()).isEqualTo("sessionId");
        assertThat(clonedCookie.getValue()).isEqualTo("updated");
        assertThat(clonedCookie.getComment()).isEqualTo("Tracks the logged in session");
        assertThat(clonedCookie.getDomain()).isEqualTo("example.com");
        assertThat(clonedCookie.isHttpOnly()).isTrue();
        assertThat(clonedCookie.getMaxAge()).isEqualTo(60);
        assertThat(clonedCookie.getPath()).isEqualTo("/catalog");
        assertThat(clonedCookie.getSecure()).isTrue();
        assertThat(clonedCookie.getVersion()).isEqualTo(1);
    }
}
