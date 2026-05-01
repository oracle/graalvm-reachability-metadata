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

import org.jdom.JDOMException;
import org.junit.jupiter.api.Test;

public class JDOMExceptionTest {
    @Test
    void messageIncludesNamingExceptionRootCause() {
        IllegalStateException rootCause = new IllegalStateException("directory service unavailable");
        NamingException namingException = new NamingException("lookup failed");
        namingException.setRootCause(rootCause);
        JDOMException exception = new JDOMException("jdom operation failed", namingException);

        String message = exception.getMessage();

        assertThat(message)
                .contains("jdom operation failed")
                .contains("lookup failed")
                .contains("directory service unavailable");
    }

    @Test
    void stackTraceIncludesNamingExceptionRootCause() {
        IllegalArgumentException rootCause = new IllegalArgumentException("invalid directory entry");
        NamingException namingException = new NamingException("name resolution failed");
        namingException.setRootCause(rootCause);
        JDOMException exception = new JDOMException("jdom stack trace", namingException);
        StringWriter output = new StringWriter();

        exception.printStackTrace(new PrintWriter(output));

        assertThat(output.toString())
                .contains("jdom stack trace")
                .contains("name resolution failed")
                .contains("Caused by: javax.naming.NamingException")
                .contains("Caused by: java.lang.IllegalArgumentException")
                .contains("invalid directory entry");
    }
}
