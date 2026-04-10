/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.javax_servlet_api;

import javax.servlet.http.HttpServlet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpServletTest {
    @Test
    void instantiatingHttpServletSubclassLoadsHttpServletResources() {
        assertNotNull(new CustomHttpServlet());
    }

    static final class CustomHttpServlet extends HttpServlet {
    }
}
