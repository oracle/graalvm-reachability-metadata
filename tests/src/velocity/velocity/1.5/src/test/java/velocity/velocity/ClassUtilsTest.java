/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.util.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    private static final String RESOURCE_PATH = "velocity/velocity/classutils-resource.txt";

    @Test
    public void resolvesClassWithSystemLoaderWhenThreadContextClassLoaderIsUnavailable() throws Exception {
        withContextClassLoader(null, () -> {
            Class<?> resolvedClass = ClassUtils.getClass(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
        });
    }

    @Test
    public void readsResourceWithClassLoaderWhenThreadContextClassLoaderIsUnavailable() throws Exception {
        withContextClassLoader(null, () -> {
            try (InputStream resource = ClassUtils.getResourceAsStream(ClassUtilsTest.class, RESOURCE_PATH)) {
                assertResourceContent(resource);
            }
        });
    }

    @Test
    public void fallsBackToClassLoaderWhenThreadContextClassLoaderCannotFindResource() throws Exception {
        try (URLClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null)) {
            withContextClassLoader(emptyClassLoader, () -> {
                try (InputStream resource = ClassUtils.getResourceAsStream(ClassUtilsTest.class, RESOURCE_PATH)) {
                    assertResourceContent(resource);
                }
            });
        }
    }

    private static void assertResourceContent(InputStream resource) throws Exception {
        assertThat(resource).isNotNull();
        assertThat(new String(resource.readAllBytes(), StandardCharsets.UTF_8)).contains("classutils-resource");
    }

    private static void withContextClassLoader(ClassLoader classLoader, ThrowingRunnable runnable) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            runnable.run();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
