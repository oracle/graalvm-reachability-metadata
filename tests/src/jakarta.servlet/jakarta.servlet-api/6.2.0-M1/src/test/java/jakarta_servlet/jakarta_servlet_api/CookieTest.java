/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

public class CookieTest {
    @Test
    void constructorInitializesCookieAndLoadsLocalizedMessages() {
        Cookie cookie = new Cookie("sessionId", "initial");

        assertThat(cookie.getName()).isEqualTo("sessionId");
        assertThat(cookie.getValue()).isEqualTo("initial");
        assertThat(cookie.getAttributes()).isEmpty();
    }

    @Test
    void constructorRejectsInvalidNameWithBundleBackedMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cookie("bad name", "value"))
                .withMessageContaining("Cookie name \"bad name\"")
                .withMessageContaining("invalid character");
    }

    @Test
    void configuredAttributesAreCaseInsensitiveAndCopiedByClone() {
        Cookie cookie = new Cookie("preferences", "compact");
        cookie.setDomain("Example.COM");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(60);
        cookie.setPath("/catalog");
        cookie.setSecure(true);
        cookie.setAttribute("SameSite", "Strict");
        cookie.setAttribute("Partitioned", "");
        cookie.setValue("expanded");

        Cookie clonedCookie = (Cookie) cookie.clone();

        assertThat(clonedCookie).isNotSameAs(cookie);
        assertThat(clonedCookie).isEqualTo(cookie);
        assertThat(clonedCookie.getName()).isEqualTo("preferences");
        assertThat(clonedCookie.getValue()).isEqualTo("expanded");
        assertThat(clonedCookie.getDomain()).isEqualTo("example.com");
        assertThat(clonedCookie.isHttpOnly()).isTrue();
        assertThat(clonedCookie.getMaxAge()).isEqualTo(60);
        assertThat(clonedCookie.getPath()).isEqualTo("/catalog");
        assertThat(clonedCookie.getSecure()).isTrue();
        assertThat(clonedCookie.getAttribute("samesite")).isEqualTo("Strict");
        assertThat(clonedCookie.getAttribute("partitioned")).isEmpty();
        assertThat(clonedCookie.getAttributes())
                .containsEntry("Domain", "example.com")
                .containsEntry("HttpOnly", "")
                .containsEntry("Max-Age", "60")
                .containsEntry("Path", "/catalog")
                .containsEntry("Secure", "")
                .containsEntry("SameSite", "Strict")
                .containsEntry("Partitioned", "");
    }
}
