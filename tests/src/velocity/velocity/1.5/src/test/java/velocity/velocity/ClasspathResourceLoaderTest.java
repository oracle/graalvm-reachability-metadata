/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.junit.jupiter.api.Test;

public class ClasspathResourceLoaderTest {
    private static final String TEMPLATE_RESOURCE = "velocity/velocity/classpath-resource-loader-template.vm";

    @Test
    public void loadsTemplateResourceFromClasspath() throws Exception {
        ClasspathResourceLoader loader = new ClasspathResourceLoader();

        try (InputStream stream = loader.getResourceStream(TEMPLATE_RESOURCE)) {
            assertThat(stream).isNotNull();
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(template).contains("Loaded through ClasspathResourceLoader");
        }
    }

    @Test
    public void wrapsClasspathLookupFailuresAsResourceNotFoundExceptions() {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        RuntimeException lookupFailure = new RuntimeException("classpath lookup failed");
        Thread.currentThread()
                .setContextClassLoader(new ThrowingResourceClassLoader(lookupFailure));

        try {
            ClasspathResourceLoader loader = new ClasspathResourceLoader();

            assertThatThrownBy(() -> loader.getResourceStream(TEMPLATE_RESOURCE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("problem with template: " + TEMPLATE_RESOURCE)
                    .hasCause(lookupFailure);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private static final class ThrowingResourceClassLoader extends ClassLoader {
        private final RuntimeException lookupFailure;

        private ThrowingResourceClassLoader(RuntimeException lookupFailure) {
            super(null);
            this.lookupFailure = lookupFailure;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            throw lookupFailure;
        }
    }
}
