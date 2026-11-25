/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CookieTest {
    @Test
    void retainsCookieSettingsAndRejectsBlankNames() {
        Cookie cookie = new Cookie("cart", "book");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/store");
        assertThat(cookie.getValue()).isEqualTo("book");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/store");
        assertThatThrownBy(() -> new Cookie("", "value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
    }
}
