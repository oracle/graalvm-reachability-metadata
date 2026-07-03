/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.CatalinaBaseConfigurationSource;
import org.apache.tomcat.util.file.ConfigurationSource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalinaBaseConfigurationSourceTest {

    private static final String STARTUP_LOCAL_STRINGS = "org/apache/catalina/startup/LocalStrings.properties";

    @TempDir
    Path catalinaBase;

    @Test
    void getResourceFallsBackToClasspathResourceWhenFileIsAbsent() throws Exception {
        Catalina catalina = configureCatalina();
        CatalinaBaseConfigurationSource source = newSource();

        try (Resource resource = source.getResource(STARTUP_LOCAL_STRINGS)) {
            String content = readUtf8(resource);

            assertThat(catalina.getConfigFile()).isEqualTo(Catalina.SERVER_XML);
            assertThat(resource.getURI().toString()).contains("LocalStrings.properties");
            assertThat(content).contains("catalinaConfigurationSource.cannotObtainURL");
        }
    }

    @Test
    void getURIFallsBackToClasspathResourceWhenFileIsAbsent() {
        Catalina catalina = configureCatalina();
        CatalinaBaseConfigurationSource source = newSource();

        assertThat(catalina.getConfigFile()).isEqualTo(Catalina.SERVER_XML);
        assertThat(source.getURI(STARTUP_LOCAL_STRINGS).toString()).contains("LocalStrings.properties");
    }

    @Test
    void getServerXmlFallsBackToLegacyClasspathResourceWhenServerXmlIsAbsent() throws Exception {
        CatalinaBaseConfigurationSource source = newSource();

        try (Resource resource = source.getServerXml()) {
            String content = readUtf8(resource);

            assertThat(resource.getURI().toString()).contains(CatalinaBaseConfigurationSource.LEGACY_SERVER_EMBED_XML);
            assertThat(content).contains("<Server");
            assertThat(content).contains("shutdown=\"SHUTDOWN\"");
        }
    }

    private CatalinaBaseConfigurationSource newSource() {
        File catalinaBaseFile = catalinaBase.toFile();
        return new CatalinaBaseConfigurationSource(catalinaBaseFile, Catalina.SERVER_XML);
    }

    private static Catalina configureCatalina() {
        Catalina catalina = new Catalina();
        catalina.setConfigFile(Catalina.SERVER_XML);
        return catalina;
    }

    private static String readUtf8(Resource resource) throws Exception {
        byte[] bytes = resource.getInputStream().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
