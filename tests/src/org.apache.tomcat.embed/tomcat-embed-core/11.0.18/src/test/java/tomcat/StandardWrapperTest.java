/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.servlets.DefaultServlet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardWrapperTest {

    @Test
    void inspectsServletMethods() throws Exception {
        StandardWrapper wrapper = new StandardWrapper();
        wrapper.setServlet(new DefaultServlet());

        assertThat(wrapper.getServletMethods()).contains("GET", "HEAD", "POST");
    }
}
