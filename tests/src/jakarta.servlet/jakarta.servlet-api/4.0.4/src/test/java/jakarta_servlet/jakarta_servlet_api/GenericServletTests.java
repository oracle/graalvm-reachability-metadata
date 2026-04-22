/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.Test;

public class GenericServletTests {
    @Test
    void getServletNameBeforeInitializationUsesBundleBackedMessage() {
        TestGenericServlet servlet = new TestGenericServlet();

        IllegalStateException exception = assertThrows(IllegalStateException.class, servlet::getServletName);

        assertThat(exception).hasMessage("ServletConfig has not been initialized");
    }

    private static final class TestGenericServlet extends GenericServlet {
        @Override
        public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        }
    }
}
