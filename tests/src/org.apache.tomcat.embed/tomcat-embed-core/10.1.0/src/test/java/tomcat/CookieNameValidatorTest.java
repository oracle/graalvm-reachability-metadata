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

public class CookieNameValidatorTest {
    @Test
    void validatesCookieNamesAsHttpTokens() {
        assertThat(new Cookie("session-id", "value").getName()).isEqualTo("session-id");
        assertThatThrownBy(() -> new Cookie("bad;name", "value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bad;name");
    }
}
