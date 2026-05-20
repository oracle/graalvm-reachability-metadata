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

public class CookieTest {

    @Test
    void createsCookie() {
        Cookie cookie = new Cookie("name", "value");

        assertThat(cookie.getName()).isEqualTo("name");
        assertThat(cookie.getValue()).isEqualTo("value");
    }
}
