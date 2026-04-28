/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_servlet;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.Test;

public class GenericServletTests {
    @Test
    void getInitParameterBeforeInitializationUsesLocalizedBundleMessage() {
        CustomGenericServlet servlet = new CustomGenericServlet();

        assertThatIllegalStateException()
                .isThrownBy(() -> servlet.getInitParameter("example"))
                .withMessage("ServletConfig has not been initialized");
    }

    static final class CustomGenericServlet extends GenericServlet {
        @Override
        public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        }
    }
}
