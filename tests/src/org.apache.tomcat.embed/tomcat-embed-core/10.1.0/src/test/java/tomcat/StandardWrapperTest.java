/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Path;

import org.apache.catalina.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardWrapperTest {
    @TempDir
    Path directory;

    @Test
    void reportsSupportedMethodsForConfiguredServlet() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            Wrapper wrapper = server.servlet(new HttpServletTest.ReadServlet());
            server.start();
            assertThat(wrapper.getServletMethods()).contains("GET", "HEAD", "OPTIONS").doesNotContain("POST");
        }
    }
}
