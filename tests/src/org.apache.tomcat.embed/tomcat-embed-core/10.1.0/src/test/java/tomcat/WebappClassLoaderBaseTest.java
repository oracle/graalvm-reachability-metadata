/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.catalina.loader.WebappClassLoaderBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappClassLoaderBaseTest {
    @TempDir
    Path directory;

    @Test
    void delegatesResourceLoadingToApplicationParent() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            server.start();
            WebappClassLoaderBase loader = (WebappClassLoaderBase) server.context().getLoader().getClassLoader();
            loader.setDelegate(true);
            assertThat(loader.getResource("tomcat/configuration.xml")).isNotNull();
            loader.setDelegate(false);
            assertThat(loader.getResource("tomcat/configuration.xml")).isNotNull();
            try (InputStream input = loader.getResourceAsStream("tomcat/configuration.xml")) {
                assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).contains("test-configuration");
            }
        }
    }
}
