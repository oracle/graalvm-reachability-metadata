/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerTest {

    @Test
    void loadsDefaultResources() {
        Server server = new Server();

        Resource styleSheet = server.getDefaultStyleSheet();
        Resource favicon = server.getDefaultFavicon();

        assertThat(styleSheet.exists()).isTrue();
        assertThat(styleSheet.getName()).endsWith("jetty-dir.css");
        assertThat(favicon.exists()).isTrue();
        assertThat(favicon.getName()).endsWith("favicon.ico");
    }
}
