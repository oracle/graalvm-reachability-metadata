/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClasspathResourceLoaderTest {
    private static final String TEMPLATE_RESOURCE = "velocity/velocity_dep/classpath-resource-loader-template.vm";

    @Test
    void opensTemplateResourceFromApplicationClasspath() throws Exception {
        ClasspathResourceLoader loader = new ClasspathResourceLoader();

        try (InputStream stream = loader.getResourceStream(TEMPLATE_RESOURCE)) {
            assertNotNull(stream);
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertEquals("Hello from the classpath resource loader.\n", template);
        }
    }
}
