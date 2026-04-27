/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathResourceLoaderTest {
    @Test
    void loadsTemplateResourceFromClasspath() throws Exception {
        ClasspathResourceLoader loader = new ClasspathResourceLoader();

        InputStream stream = loader.getResourceStream("velocity/velocity_dep/classpath-loader-template.vm");

        assertThat(stream).isNotNull();
        try (InputStream closeableStream = stream) {
            String template = new String(closeableStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(template).contains("Loaded by ClasspathResourceLoader");
        }
    }
}
