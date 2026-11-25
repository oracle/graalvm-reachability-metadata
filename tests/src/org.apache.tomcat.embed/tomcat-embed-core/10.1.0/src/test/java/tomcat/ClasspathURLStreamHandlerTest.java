/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.webresources.ClasspathURLStreamHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathURLStreamHandlerTest {
    @Test
    void opensClasspathResourceThroughUrlHandler() throws Exception {
        URL url = new URL(null, "classpath:tomcat/configuration.xml", new ClasspathURLStreamHandler());
        try (InputStream input = url.openStream()) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).contains("test-configuration");
        }
    }
}
