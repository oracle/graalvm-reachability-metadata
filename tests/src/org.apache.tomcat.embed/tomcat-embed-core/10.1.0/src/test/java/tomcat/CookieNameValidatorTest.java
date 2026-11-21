/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CookieNameValidatorTest {
    @Test
    void rejectsBlankAndNonTokenCookieNames() {
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class, () -> new Cookie("", "value"));
        IllegalArgumentException separator = assertThrows(IllegalArgumentException.class,
                () -> new Cookie("bad;name", "value"));
        assertFalse(blank.getMessage().isBlank());
        assertFalse(separator.getMessage().isBlank());
    }
}
