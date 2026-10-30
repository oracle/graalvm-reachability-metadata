/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jsp_api;

import org.junit.jupiter.api.Test;

import javax.servlet.jsp.JspException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class Jsp_apiTest {
    @Test
    void preservesPageFailureMessageAndRootCause() {
        IllegalArgumentException cause = new IllegalArgumentException("Invalid page parameter");
        JspException exception = new JspException("Page rendering failed", cause);

        assertEquals("Page rendering failed", exception.getMessage());
        assertSame(cause, exception.getRootCause());
    }
}
