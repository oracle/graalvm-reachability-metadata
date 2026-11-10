/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpServletInnerTomcatHackTest {
    @TempDir
    File base;

    @Test
    void optionsHonorsConnectorTracePolicy() throws Exception {
        try (EmbeddedApplication application = new EmbeddedApplication(base)) {
            application.addServlet(new HttpServletTest.ReadOnlyServlet());
            application.server.getConnector().setAllowTrace(false);
            application.start();
            String disabled = application.request("OPTIONS").headers().firstValue("Allow").orElseThrow();
            assertFalse(disabled.contains("TRACE"));
            application.server.getConnector().setAllowTrace(true);
            String enabled = application.request("OPTIONS").headers().firstValue("Allow").orElseThrow();
            assertTrue(enabled.contains("TRACE"));
        }
    }
}
