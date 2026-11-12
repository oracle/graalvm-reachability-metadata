/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Path;

import org.apache.catalina.loader.WebappClassLoader;
import org.apache.catalina.loader.WebappLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappLoaderTest {
    @TempDir
    Path directory;

    @Test
    void constructsConfiguredWebApplicationLoader() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            WebappLoader loader = new WebappLoader();
            loader.setLoaderClass(WebappClassLoader.class.getName());
            server.context().setLoader(loader);
            server.start();
            assertThat(loader.getClassLoader()).isInstanceOf(WebappClassLoader.class);
            assertThat(loader.getClassLoader().getResource("tomcat/configuration.xml")).isNotNull();
        }
    }
}
