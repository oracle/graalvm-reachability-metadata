/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.util.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    private static final String TEMPLATE_RESOURCE = "velocity/velocity/classpath-resource-loader-template.vm";

    @Test
    void fallsBackToSystemClassLoaderWhenContextClassLoaderCannotLoadClass() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        });

        try {
            Class<?> loadedClass = ClassUtils.getClass(String.class.getName());

            assertThat(loadedClass).isEqualTo(String.class);
        } finally {
            thread.setContextClassLoader(previousLoader);
        }
    }

    @Test
    void loadsResourceFromClassLoaderWhenNoContextClassLoaderIsAvailable() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);

        try (InputStream stream = ClassUtils.getResourceAsStream(ClassUtilsTest.class, TEMPLATE_RESOURCE)) {
            assertTemplateResource(stream);
        } finally {
            thread.setContextClassLoader(previousLoader);
        }
    }

    @Test
    void fallsBackToClassLoaderWhenContextClassLoaderCannotFindResource() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return null;
            }
        });

        try (InputStream stream = ClassUtils.getResourceAsStream(ClassUtilsTest.class, TEMPLATE_RESOURCE)) {
            assertTemplateResource(stream);
        } finally {
            thread.setContextClassLoader(previousLoader);
        }
    }

    private static void assertTemplateResource(InputStream stream) throws Exception {
        assertThat(stream).isNotNull();
        String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(template).contains("Loaded through ClasspathResourceLoader");
    }
}
