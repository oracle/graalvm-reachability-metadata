/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SSLImplementationTest {
    @Test
    void resolvesNamedJsseImplementationAndReadsSession() throws Exception {
        SSLImplementation implementation = SSLImplementation.getInstance(JSSEImplementation.class.getName());
        SSLSupport support = implementation.getSSLSupport(SSLContext.getDefault().createSSLEngine().getSession(), Map.of());
        assertNotNull(support);
        assertEquals("SSL_NULL_WITH_NULL_NULL", support.getCipherSuite());
    }
}
