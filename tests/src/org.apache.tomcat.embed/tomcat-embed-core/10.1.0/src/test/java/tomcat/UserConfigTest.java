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
import org.apache.catalina.startup.HomesUserDatabase;
import org.apache.catalina.startup.UserConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class UserConfigTest {
    @TempDir
    Path directory;

    @Test
    void deploysWebApplicationFromConfiguredUserHomes() throws Exception {
        Path homes = Files.createDirectories(directory.resolve("homes"));
        Path application = Files.createDirectories(homes.resolve("reader/public_html"));
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            UserConfig configuration = new UserConfig();
            configuration.setHomeBase(homes.toString());
            configuration.setUserClass(HomesUserDatabase.class.getName());
            configuration.setAllow("reader");
            server.tomcat().getHost().addLifecycleListener(configuration);
            server.start();
            Context deployed = (Context) server.tomcat().getHost().findChild("/~reader");
            assertThat(deployed).isNotNull();
            assertThat(deployed.getState().isAvailable()).isTrue();
            assertThat(deployed.getDocBase()).isEqualTo(application.toString());
        }
    }
}
