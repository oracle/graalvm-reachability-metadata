/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SSLImplementationTest {

    @Test
    void createsNamedSslImplementation() throws Exception {
        SSLImplementation implementation = SSLImplementation.getInstance(JSSEImplementation.class.getName());

        assertThat(implementation).isInstanceOf(JSSEImplementation.class);
    }
}
