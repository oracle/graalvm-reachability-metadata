/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.JDOMException;
import org.junit.jupiter.api.Test;

import javax.naming.NamingException;

import static org.assertj.core.api.Assertions.assertThat;

public class JDOMExceptionTest {
    @Test
    void messageIncludesRootCauseFromNamingException() throws Exception {
        NamingException namingException = new NamingException("directory lookup failed");
        namingException.setRootCause(new IllegalStateException("naming provider unavailable"));
        JDOMException exception = new JDOMException("xml processing failed", namingException);

        assertThat(exception.getCause()).isSameAs(namingException);
        assertThat(exception.getMessage())
                .contains("xml processing failed")
                .contains("directory lookup failed")
                .contains("naming provider unavailable");
    }
}
