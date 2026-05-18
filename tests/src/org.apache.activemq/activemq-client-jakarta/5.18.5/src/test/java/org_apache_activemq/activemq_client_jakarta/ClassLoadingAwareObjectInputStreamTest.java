/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.Command;
import org.apache.activemq.util.ClassLoadingAwareObjectInputStream;
import org.junit.jupiter.api.Test;

public class ClassLoadingAwareObjectInputStreamTest {
    private static final String ACTIVE_MQ_QUEUE_NAME = ActiveMQQueue.class.getName();
    private static final String COMMAND_INTERFACE_NAME = Command.class.getName();

    @Test
    void resolvesClassWithThreadContextClassLoader() throws Exception {
        ObjectStreamClass streamClass = ObjectStreamClass.lookup(ActiveMQQueue.class);

        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream()) {
            Class<?> resolvedClass = withThreadContextClassLoader(
                    currentClassLoader(), () -> inputStream.resolve(streamClass));

            assertThat(resolvedClass).isEqualTo(ActiveMQQueue.class);
        }
    }

    @Test
    void resolvesClassWithFallbackClassLoaderWhenContextAndInputLoadersRejectIt() throws Exception {
        ObjectStreamClass streamClass = ObjectStreamClass.lookup(ActiveMQQueue.class);
        ClassLoader rejectingClassLoader = new RejectingClassLoader(ACTIVE_MQ_QUEUE_NAME, currentClassLoader());

        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream()) {
            Class<?> resolvedClass = withThreadContextClassLoader(
                    rejectingClassLoader, () -> inputStream.resolve(streamClass));

            assertThat(resolvedClass).isEqualTo(ActiveMQQueue.class);
        }
    }

    @Test
    void resolvesProxyClassWithThreadContextClassLoader() throws Exception {
        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream()) {
            inputStream.setTrustAllPackages(true);

            Class<?> resolvedClass = withThreadContextClassLoader(
                    currentClassLoader(), () -> inputStream.resolveProxy(COMMAND_INTERFACE_NAME));

            assertThat(resolvedClass.getInterfaces()).containsExactly(Command.class);
        }
    }

    @Test
    void resolvesProxyClassWithInputLoaderWhenContextLoaderCannotDefineIt() throws Exception {
        ClassLoader rejectingClassLoader = new RejectingClassLoader(COMMAND_INTERFACE_NAME, currentClassLoader());

        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStreamWithApplicationLoadedInput()) {
            inputStream.setTrustAllPackages(true);

            Class<?> resolvedClass = withThreadContextClassLoader(
                    rejectingClassLoader, () -> inputStream.resolveProxy(COMMAND_INTERFACE_NAME));

            assertThat(resolvedClass.getInterfaces()).containsExactly(Command.class);
        }
    }

    @Test
    void resolvesProxyClassWithFallbackLoaderWhenContextAndInputLoadersCannotDefineIt() throws Exception {
        ClassLoader rejectingClassLoader = new RejectingClassLoader(COMMAND_INTERFACE_NAME, currentClassLoader());

        try (ExposedClassLoadingAwareObjectInputStream inputStream = newObjectInputStream()) {
            inputStream.setTrustAllPackages(true);

            Class<?> resolvedClass = withThreadContextClassLoader(
                    rejectingClassLoader, () -> inputStream.resolveProxy(COMMAND_INTERFACE_NAME));

            assertThat(resolvedClass.getInterfaces()).containsExactly(Command.class);
        }
    }

    private static ExposedClassLoadingAwareObjectInputStream newObjectInputStream() throws IOException {
        return new ExposedClassLoadingAwareObjectInputStream(new ByteArrayInputStream(emptyObjectStreamBytes()));
    }

    private static ExposedClassLoadingAwareObjectInputStream newObjectInputStreamWithApplicationLoadedInput()
            throws IOException {
        return new ExposedClassLoadingAwareObjectInputStream(
                new ApplicationLoadedByteArrayInputStream(emptyObjectStreamBytes()));
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
        private ExposedClassLoadingAwareObjectInputStream(ByteArrayInputStream inputStream) throws IOException {
            super(inputStream);
        }

        private Class<?> resolve(ObjectStreamClass classDesc) throws IOException, ClassNotFoundException {
            return super.resolveClass(classDesc);
        }

        private Class<?> resolveProxy(String interfaceName) throws IOException, ClassNotFoundException {
            return super.resolveProxyClass(new String[] {interfaceName});
        }
    }

    private static final class ApplicationLoadedByteArrayInputStream extends ByteArrayInputStream {
        private ApplicationLoadedByteArrayInputStream(byte[] buf) {
            super(buf);
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
