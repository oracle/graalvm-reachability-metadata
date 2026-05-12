/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.util.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    private static final String TEST_RESOURCE = "org_apache_velocity/velocity/class-utils-resource.txt";

    @Test
    void resolvesClassWithContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ClassUtilsTest.class.getClassLoader());

            Class<?> resolvedClass = ClassUtils.getClass(InstanceTarget.class.getName());

            assertThat(resolvedClass).isSameAs(InstanceTarget.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToDefaultClassLoaderWhenContextClassLoaderCannotResolveClass() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new RejectingClassLoader(InstanceTarget.class.getName()));

            Class<?> resolvedClass = ClassUtils.getClass(InstanceTarget.class.getName());

            assertThat(resolvedClass).isSameAs(InstanceTarget.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createsNewInstanceByClassName() throws Exception {
        Object instance = ClassUtils.getNewInstance(InstanceTarget.class.getName());

        assertThat(instance).isInstanceOf(InstanceTarget.class);
        assertThat(((InstanceTarget) instance).message()).isEqualTo("created");
    }

    @Test
    void loadsResourceFromReferenceClassLoaderWhenContextClassLoaderIsNull() throws IOException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);

            InputStream resource = ClassUtils.getResourceAsStream(ClassUtilsTest.class, "/" + TEST_RESOURCE);

            assertThat(read(resource)).isEqualTo("class-utils-test-resource");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadsResourceFromContextClassLoaderFirst() throws IOException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(
                    new InMemoryResourceClassLoader(TEST_RESOURCE, "context-loader"));

            InputStream resource = ClassUtils.getResourceAsStream(ClassUtilsTest.class, TEST_RESOURCE);

            assertThat(read(resource)).isEqualTo("context-loader");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToReferenceClassLoaderWhenContextClassLoaderHasNoResource() throws IOException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new EmptyResourceClassLoader());

            InputStream resource = ClassUtils.getResourceAsStream(ClassUtilsTest.class, TEST_RESOURCE);

            assertThat(read(resource)).isEqualTo("class-utils-test-resource");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static String read(InputStream stream) throws IOException {
        assertThat(stream).isNotNull();
        try (InputStream resource = stream) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    public static class InstanceTarget {
        public String message() {
            return "created";
        }
    }

    private static class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        RejectingClassLoader(String rejectedClassName) {
            super(null);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static class InMemoryResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final String content;

        InMemoryResourceClassLoader(String resourceName, String content) {
            super(null);
            this.resourceName = resourceName;
            this.content = content;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!resourceName.equals(name)) {
                return null;
            }
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static class EmptyResourceClassLoader extends ClassLoader {
        EmptyResourceClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return null;
        }
    }
}
