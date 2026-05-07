/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.naming.NamingException;
import javax.servlet.ServletException;

import org.jdom.JDOMException;
import org.junit.jupiter.api.Test;

public class JDOMExceptionTest {
    @Test
    void includesNamingExceptionRootCauseInNestedMessage() {
        NamingException namingException = new NamingException("lookup failed");
        namingException.setRootCause(new IllegalStateException("directory unavailable"));
        JDOMException exception = new JDOMException("jdom failure", namingException);

        String message = exception.getMessage();

        assertThat(message)
                .contains("jdom failure")
                .contains("lookup failed")
                .contains("directory unavailable");
    }

    @Test
    void printsNamingExceptionRootCauseInNestedStackTrace() {
        NamingException namingException = new NamingException("lookup failed");
        namingException.setRootCause(new IllegalStateException("directory unavailable"));
        JDOMException exception = new JDOMException("jdom failure", namingException);
        StringWriter stackTrace = new StringWriter();

        exception.printStackTrace(new PrintWriter(stackTrace));

        assertThat(stackTrace.toString())
                .contains("jdom failure")
                .contains("lookup failed")
                .contains("directory unavailable");
    }

    @Test
    void includesServletExceptionRootCauseInNestedMessage() {
        ServletException servletException = new ServletException(
                "request dispatch failed", new IllegalArgumentException("invalid route"));
        JDOMException exception = new JDOMException("jdom failure", servletException);

        String message = exception.getMessage();

        assertThat(message)
                .contains("jdom failure")
                .contains("request dispatch failed")
                .contains("invalid route");
    }
}
