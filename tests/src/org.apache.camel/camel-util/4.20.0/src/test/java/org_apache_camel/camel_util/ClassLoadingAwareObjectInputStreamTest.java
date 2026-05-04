/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;

import org.apache.camel.util.ClassLoadingAwareObjectInputStream;
import org.apache.camel.util.function.ThrowingSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoadingAwareObjectInputStreamTest {
    private static final String PROXY_CONTRACT_NAME = ThrowingSupplier.class.getName();
    private static final String SERIALIZABLE_PAYLOAD_NAME = String.class.getName();

    @Test
    void resolvesClassWithThreadContextClassLoader() throws Exception {
        ObjectStreamClass streamClass = ObjectStreamClass.lookup(String.class);

        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream(currentClassLoader())) {
            Class<?> resolvedClass = withThreadContextClassLoader(
                    currentClassLoader(), () -> inputStream.resolve(streamClass));

            assertThat(resolvedClass).isEqualTo(String.class);
        }
    }

    @Test
    void resolvesClassWithFallbackClassLoaderWhenContextAndInputLoadersRejectIt() throws Exception {
        ObjectStreamClass streamClass = ObjectStreamClass.lookup(String.class);
        ClassLoader rejectingClassLoader = new RejectingClassLoader(SERIALIZABLE_PAYLOAD_NAME, currentClassLoader());

        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream(rejectingClassLoader)) {
            Class<?> resolvedClass = withThreadContextClassLoader(
                    rejectingClassLoader, () -> inputStream.resolve(streamClass));

            assertThat(resolvedClass).isEqualTo(String.class);
        }
    }

    @Test
    void resolvesProxyClassWithThreadContextClassLoader() throws Exception {
        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream(currentClassLoader())) {
            Class<?> resolvedClass = withThreadContextClassLoader(
                    currentClassLoader(), () -> inputStream.resolveProxy(PROXY_CONTRACT_NAME));

            assertProxyContract(resolvedClass);
        }
    }

    @Test
    void resolvesProxyClassWithInputLoaderWhenContextLoaderCannotDefineIt() throws Exception {
        ClassLoader rejectingClassLoader = new RejectingClassLoader(PROXY_CONTRACT_NAME, currentClassLoader());

        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream(currentClassLoader())) {
            Class<?> resolvedClass = withThreadContextClassLoader(
                    rejectingClassLoader, () -> inputStream.resolveProxy(PROXY_CONTRACT_NAME));

            assertProxyContract(resolvedClass);
        }
    }

    @Test
    void resolvesProxyClassWithFallbackLoaderWhenContextAndInputLoadersCannotDefineIt() throws Exception {
        ClassLoader rejectingClassLoader = new RejectingClassLoader(PROXY_CONTRACT_NAME, currentClassLoader());

        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream(rejectingClassLoader)) {
            Class<?> resolvedClass = withThreadContextClassLoader(
                    rejectingClassLoader, () -> inputStream.resolveProxy(PROXY_CONTRACT_NAME));

            assertProxyContract(resolvedClass);
        }
    }

    private static void assertProxyContract(Class<?> resolvedClass) {
        assertThat(Proxy.isProxyClass(resolvedClass)).isTrue();
        assertThat(resolvedClass.getInterfaces()).containsExactly(ThrowingSupplier.class);
    }

    private static ExposedClassLoadingAwareObjectInputStream newObjectInputStream(ClassLoader classLoader)
            throws IOException {
        return new ExposedClassLoadingAwareObjectInputStream(classLoader, emptyObjectStreamBytes());
    }

    private static byte[] emptyObjectStreamBytes() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.flush();
        }

        return outputStream.toByteArray();
    }

    private static ClassLoader currentClassLoader() {
        return ClassLoadingAwareObjectInputStreamTest.class.getClassLoader();
    }

    private static <T> T withThreadContextClassLoader(ClassLoader classLoader, CheckedSupplier<T> supplier)
            throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static final class ExposedClassLoadingAwareObjectInputStream extends ClassLoadingAwareObjectInputStream {
        private ExposedClassLoadingAwareObjectInputStream(ClassLoader classLoader, byte[] streamBytes)
                throws IOException {
            super(classLoader, new ByteArrayInputStream(streamBytes));
        }

        private Class<?> resolve(ObjectStreamClass classDesc) throws IOException, ClassNotFoundException {
            return super.resolveClass(classDesc);
        }

        private Class<?> resolveProxy(String interfaceName) throws IOException, ClassNotFoundException {
            return super.resolveProxyClass(new String[] {interfaceName});
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(String rejectedClassName, ClassLoader parent) {
            super(parent);
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
}
