/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.junit.jupiter.api.Test;

public class ClasspathResourceLoaderTest {
    @Test
    void opensTemplateFromClasspath() throws Exception {
        final ClasspathResourceLoader loader = new ClasspathResourceLoader();

        try (InputStream stream = loader.getResourceStream("velocity/velocity_dep/classpath-template.vm")) {
            assertThat(stream).isNotNull();
            final String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(template).contains("Hello from the classpath loader");
        }
    }
}
