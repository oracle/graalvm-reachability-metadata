/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThatCode;

import javax.servlet.http.HttpServlet;

import org.junit.jupiter.api.Test;

class HttpServletTests {
    @Test
    void instantiatingHttpServletSubclassLoadsLocalizedMessages() {
        assertThatCode(CustomHttpServlet::new)
                .doesNotThrowAnyException();
    }

    static final class CustomHttpServlet extends HttpServlet {
    }
}
