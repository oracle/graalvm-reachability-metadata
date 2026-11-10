/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.catalina.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StandardWrapperTest {
    @TempDir
    File base;

    @Test
    void reportsMethodsSupportedByItsServlet() throws Exception {
        try (EmbeddedApplication application = new EmbeddedApplication(base)) {
            Wrapper wrapper = application.addServlet(new HttpServletTest.ReadOnlyServlet());
            application.start();
            List<String> methods = Arrays.asList(wrapper.getServletMethods());
            assertTrue(methods.containsAll(List.of("GET", "HEAD", "OPTIONS")));
            assertFalse(methods.contains("POST"));
        }
    }
}
