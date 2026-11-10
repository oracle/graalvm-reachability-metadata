/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.startup.CatalinaBaseConfigurationSource;
import org.apache.tomcat.util.file.ConfigurationSource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CatalinaBaseConfigurationSourceTest {
    @TempDir
    File base;

    @Test
    void readsClasspathConfigurationAndResolvesItsUri() throws Exception {
        CatalinaBaseConfigurationSource source = new CatalinaBaseConfigurationSource(base, "tomcat/config.txt");
        try (Resource resource = source.getResource("tomcat/config.txt")) {
            assertEquals("mode=embedded\n", new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(resource.getURI(), source.getURI("tomcat/config.txt"));
        }
        try (Resource resource = source.getServerXml()) {
            assertEquals("mode=embedded\n", new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
