/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_toolchain.jetty_jakarta_servlet_api;

import java.io.IOException;

import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GenericServletTests {
    @Test
    void getServletNameBeforeInitializationUsesBundleBackedMessage() {
        TestGenericServlet servlet = new TestGenericServlet();

        IllegalStateException exception = assertThrows(IllegalStateException.class, servlet::getServletName);

        assertThat(exception).hasMessage("ServletConfig has not been initialized");
    }

    private static final class TestGenericServlet extends GenericServlet {
        @Override
        public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        }
    }
}
