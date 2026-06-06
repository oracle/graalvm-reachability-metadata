/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.util.ClassUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class VelocityClassUtilsTest {
    private static final String RESOURCE_PATH = "io_sundr/sundr_codegen_velocity_nodeps/velocity-classutils-resource.txt";

    @Test
    void getClassUsesThreadContextClassLoaderWhenAvailable() throws Exception {
        withContextClassLoader(VelocityClassUtilsTest.class.getClassLoader(), () -> {
            Class resolvedClass = ClassUtils.getClass(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
        });
    }

    @Test
    void getClassFallsBackToClassForNameWhenThreadContextClassLoaderIsUnavailable() throws Exception {
        withContextClassLoader(null, () -> {
            Class resolvedClass = ClassUtils.getClass(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
        });
    }

    @Test
    void getNewInstanceCreatesDefaultConstructedObjectFromClassName() throws Exception {
        withContextClassLoader(VelocityClassUtilsTest.class.getClassLoader(), () -> {
            Object instance = ClassUtils.getNewInstance(String.class.getName());

            assertThat(instance).isEqualTo("");
        });
    }

    @Test
    void getResourceAsStreamReadsWithClassLoaderWhenThreadContextClassLoaderIsUnavailable() throws Exception {
        withContextClassLoader(null, () -> {
            try (InputStream resource = ClassUtils.getResourceAsStream(VelocityClassUtilsTest.class, RESOURCE_PATH)) {
                assertResourceContent(resource);
            }
        });
    }

    @Test
    void getResourceAsStreamReadsWithThreadContextClassLoader() throws Exception {
        withContextClassLoader(VelocityClassUtilsTest.class.getClassLoader(), () -> {
            try (InputStream resource = ClassUtils.getResourceAsStream(VelocityClassUtilsTest.class, "/" + RESOURCE_PATH)) {
                assertResourceContent(resource);
            }
        });
    }

    @Test
    void getResourceAsStreamFallsBackToClassLoaderWhenThreadContextClassLoaderMissesResource() throws Exception {
        withContextClassLoader(ClassLoader.getPlatformClassLoader(), () -> {
            try (InputStream resource = ClassUtils.getResourceAsStream(VelocityClassUtilsTest.class, RESOURCE_PATH)) {
                assertResourceContent(resource);
            }
        });
    }

    private static void assertResourceContent(InputStream resource) throws Exception {
        assertThat(resource).isNotNull();
        assertThat(new String(resource.readAllBytes(), StandardCharsets.UTF_8)).contains("velocity-classutils-resource");
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
