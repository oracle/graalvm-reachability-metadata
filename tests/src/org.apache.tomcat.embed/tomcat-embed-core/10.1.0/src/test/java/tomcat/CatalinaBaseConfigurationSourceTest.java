/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.catalina.startup.CatalinaBaseConfigurationSource;
import org.apache.tomcat.util.file.ConfigurationSource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalinaBaseConfigurationSourceTest {
    @TempDir
    Path directory;

    @Test
    void loadsClasspathConfigurationAndResolvesItsUri() throws Exception {
        CatalinaBaseConfigurationSource source = new CatalinaBaseConfigurationSource(directory.toFile(),
                "tomcat/configuration.xml");
        try (Resource resource = source.getServerXml()) {
            assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                    .contains("test-configuration");
            assertThat(source.getURI("tomcat/configuration.xml")).isEqualTo(resource.getURI());
        }
    }
}
