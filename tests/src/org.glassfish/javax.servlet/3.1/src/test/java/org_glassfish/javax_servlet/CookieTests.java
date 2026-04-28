/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_servlet;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

public class CookieTests {
    @Test
    void constructorRejectsReservedTokenNameWithLocalizedMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cookie("Path", "value"))
                .withMessage("Cookie name \"Path\" is a reserved token");
    }
}
