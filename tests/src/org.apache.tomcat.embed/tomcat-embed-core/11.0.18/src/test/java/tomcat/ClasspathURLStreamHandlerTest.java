/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.webresources.ClasspathURLStreamHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathURLStreamHandlerTest {

    @Test
    void opensClasspathResource() throws Exception {
        URL url = new URL(null, "classpath:tomcat-test-resource.txt", new ClasspathURLStreamHandler());

        try (var stream = url.openStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("tomcat resource\n");
        }
    }
}
