/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import javax.naming.NamingException;

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
}
