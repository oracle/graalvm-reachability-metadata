/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpServletInnerTomcatHackTest {
    @TempDir
    Path directory;

    @Test
    void honorsConnectorTracePolicyInOptionsResponse() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            server.servlet(new HttpServletTest.ReadServlet());
            server.tomcat().getConnector().setAllowTrace(false);
            server.start();
            assertThat(server.request("OPTIONS", "/").headers().firstValue("Allow").orElseThrow())
                    .doesNotContain("TRACE");
            server.tomcat().getConnector().setAllowTrace(true);
            assertThat(server.request("OPTIONS", "/").headers().firstValue("Allow").orElseThrow())
                    .contains("TRACE");
        }
    }
}
