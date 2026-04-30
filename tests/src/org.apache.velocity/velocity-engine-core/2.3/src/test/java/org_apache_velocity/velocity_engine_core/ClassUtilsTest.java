/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.util.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    private static final String RESOURCE_PATH = "org_apache_velocity/velocity_engine_core/classutils-resource.txt";

    @Test
    void resolvesClassWithThreadContextClassLoader() throws Exception {
        withContextClassLoader(ClassUtilsTest.class.getClassLoader(), () -> {
            Class<?> resolvedClass = ClassUtils.getClass(VelocityEngine.class.getName());

            assertThat(resolvedClass).isSameAs(VelocityEngine.class);
        });
    }

    @Test
    void resolvesClassWithSystemLoaderWhenThreadContextClassLoaderIsUnavailable() throws Exception {
        withContextClassLoader(null, () -> {
            Class<?> resolvedClass = ClassUtils.getClass(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
        });
    }

    @Test
    void createsNewInstanceFromResolvedClassName() throws Exception {
        withContextClassLoader(ClassUtilsTest.class.getClassLoader(), () -> {
            Object instance = ClassUtils.getNewInstance(VelocityEngine.class.getName());

            assertThat(instance).isInstanceOf(VelocityEngine.class);
        });
    }

    @Test
    void readsResourceWithClassLoaderWhenThreadContextClassLoaderIsUnavailable() throws Exception {
        withContextClassLoader(null, () -> {
            try (InputStream resource = ClassUtils.getResourceAsStream(ClassUtilsTest.class, RESOURCE_PATH)) {
                assertResourceContent(resource);
            }
        });
    }

    @Test
    void readsResourceFromThreadContextClassLoader() throws Exception {
        withContextClassLoader(ClassUtilsTest.class.getClassLoader(), () -> {
            try (InputStream resource = ClassUtils.getResourceAsStream(ClassUtilsTest.class, "/" + RESOURCE_PATH)) {
                assertResourceContent(resource);
            }
        });
    }

    @Test
    void fallsBackToClassLoaderWhenThreadContextClassLoaderCannotFindResource() throws Exception {
        ClassLoader platformClassLoader = ClassLoader.getPlatformClassLoader();

        withContextClassLoader(platformClassLoader, () -> {
            try (InputStream resource = ClassUtils.getResourceAsStream(ClassUtilsTest.class, RESOURCE_PATH)) {
                assertResourceContent(resource);
            }
        });
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
