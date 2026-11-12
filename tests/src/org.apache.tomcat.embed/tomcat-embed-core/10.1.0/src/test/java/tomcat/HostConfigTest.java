/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.Context;
import org.apache.catalina.startup.HostConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class HostConfigTest {
    @TempDir
    Path directory;

    @Test
    void deploysApplicationDirectoryWhenHostStarts() throws Exception {
        Path applications = Files.createDirectories(directory.resolve("applications"));
        Files.createDirectories(applications.resolve("catalog/WEB-INF"));
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            server.tomcat().getHost().setAppBase(applications.toString());
            server.tomcat().getHost().addLifecycleListener(new HostConfig());
            server.start();
            Context deployed = (Context) server.tomcat().getHost().findChild("/catalog");
            assertThat(deployed).isNotNull();
            assertThat(deployed.getPath()).isEqualTo("/catalog");
            assertThat(deployed.getState().isAvailable()).isTrue();
        }
    }
}
