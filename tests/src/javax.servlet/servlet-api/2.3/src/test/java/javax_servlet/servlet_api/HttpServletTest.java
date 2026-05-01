/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.servlet_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

public class HttpServletTest {
    @Test
    void defaultLastModifiedIsUnknownAfterLoadingHttpServletResources() {
        final ExposedHttpServlet servlet = new ExposedHttpServlet();

        assertThat(servlet.invokeGetLastModified()).isEqualTo(-1L);
    }

    private static final class ExposedHttpServlet extends HttpServlet {
        long invokeGetLastModified() {
            final HttpServletRequest request = null;

            return super.getLastModified(request);
        }
    }
}
