/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.junit.jupiter.api.Test;

public class ClassLoaderObjectInputStreamTest {

    @Test
    void resolvesClassesWithTheProvidedClassLoader() throws Exception {
        ObjectStreamClass objectStreamClass = ObjectStreamClass.lookup(SerializablePayload.class);

        try (ExposedClassLoaderObjectInputStream inputStream = new ExposedClassLoaderObjectInputStream(
                SerializablePayload.class.getClassLoader())) {
            Class<?> resolvedClass = inputStream.resolveClassDescriptor(objectStreamClass);

            assertThat(resolvedClass).isEqualTo(SerializablePayload.class);
        }
    }

    @Test
    void fallsBackToObjectInputStreamClassResolutionWhenTheProvidedLoaderCannotResolveTheClass() throws Exception {
        ObjectStreamClass objectStreamClass = ObjectStreamClass.lookup(String.class);
        ClassLoader rejectingClassLoader = new RejectingClassLoader(
                String.class.getName(),
                ClassLoaderObjectInputStreamTest.class.getClassLoader());

        try (ExposedClassLoaderObjectInputStream inputStream = new ExposedClassLoaderObjectInputStream(
                rejectingClassLoader)) {
            Class<?> resolvedClass = inputStream.resolveClassDescriptor(objectStreamClass);

            assertThat(resolvedClass).isEqualTo(String.class);
        }
    }

    @Test
    void resolvesProxyClassesWithTheProvidedClassLoader() throws Exception {
        String[] interfaceNames = new String[] {NamedProxyContract.class.getName()};

        try (ExposedClassLoaderObjectInputStream inputStream = new ExposedClassLoaderObjectInputStream(
                NamedProxyContract.class.getClassLoader())) {
            Class<?> resolvedClass = inputStream.resolveProxyType(interfaceNames);

            assertThat(Proxy.isProxyClass(resolvedClass)).isTrue();
            assertThat(resolvedClass.getInterfaces()).containsExactly(NamedProxyContract.class);
        }
    }

    @Test
    void fallsBackToObjectInputStreamProxyResolutionWhenTheProvidedLoaderCannotDefineTheProxy() throws Exception {
        String[] interfaceNames = new String[] {NonPublicProxyContract.class.getName()};
        ClassLoader childClassLoader = new ClassLoader(NonPublicProxyContract.class.getClassLoader()) {
        };

        try (ExposedClassLoaderObjectInputStream inputStream = new ExposedClassLoaderObjectInputStream(
                childClassLoader)) {
            Class<?> resolvedClass = inputStream.resolveProxyType(interfaceNames);

            assertThat(Proxy.isProxyClass(resolvedClass)).isTrue();
            assertThat(resolvedClass.getInterfaces()).containsExactly(NonPublicProxyContract.class);
        }
    }

    private static byte[] emptyObjectStreamBytes() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.flush();
        }

        return outputStream.toByteArray();
    }

    private interface NamedProxyContract extends Serializable {
        String value();
    }

    interface NonPublicProxyContract extends Serializable {
        String value();
    }

    private static final class SerializablePayload implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String value;

        private SerializablePayload(String value) {
            this.value = value;
        }

        private String getValue() {
            return value;
        }
    }

    private static final class ExposedClassLoaderObjectInputStream extends ClassLoaderObjectInputStream {

        private ExposedClassLoaderObjectInputStream(ClassLoader classLoader) throws IOException {
            super(classLoader, new ByteArrayInputStream(emptyObjectStreamBytes()));
        }

        private Class<?> resolveClassDescriptor(ObjectStreamClass objectStreamClass)
                throws IOException, ClassNotFoundException {
            return super.resolveClass(objectStreamClass);
        }

        private Class<?> resolveProxyType(String[] interfaces) throws IOException, ClassNotFoundException {
            return super.resolveProxyClass(interfaces);
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
