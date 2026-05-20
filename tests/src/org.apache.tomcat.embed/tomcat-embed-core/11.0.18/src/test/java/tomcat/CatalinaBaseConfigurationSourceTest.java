/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.nio.file.Files;

import org.apache.catalina.startup.CatalinaBaseConfigurationSource;
import org.apache.tomcat.util.file.ConfigurationSource.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalinaBaseConfigurationSourceTest {

    @Test
    void resolvesClasspathResourcesAndLegacyServerXml() throws Exception {
        File base = Files.createTempDirectory("catalina-base").toFile();
        CatalinaBaseConfigurationSource source = new CatalinaBaseConfigurationSource(base, "missing-server.xml");

        try (Resource serverXml = source.getServerXml();
                Resource resource = source.getResource("tomcat-test-resource.txt")) {
            assertThat(serverXml.getURI().toString()).endsWith("server-embed.xml");
            assertThat(resource.getURI().toString()).endsWith("tomcat-test-resource.txt");
        }
        assertThat(source.getURI("tomcat-test-resource.txt").toString()).endsWith("tomcat-test-resource.txt");
    }
}
