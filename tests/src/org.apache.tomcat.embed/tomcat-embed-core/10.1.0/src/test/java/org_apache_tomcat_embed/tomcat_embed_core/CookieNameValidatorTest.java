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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class CookieNameValidatorTest {

    @Test
    void validatesCookieNamesThroughPublicConstructor() {
        Cookie cookie = new Cookie("valid-cookie_name", "value");

        assertThat(cookie.getName()).isEqualTo("valid-cookie_name");
        assertThatIllegalArgumentException().isThrownBy(() -> new Cookie("invalid;name", "value"));
    }
}
