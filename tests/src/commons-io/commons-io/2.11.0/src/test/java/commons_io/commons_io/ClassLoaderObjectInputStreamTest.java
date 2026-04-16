/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassLoaderObjectInputStreamTest {
    @Test
    void resolvesSerializableClassesWithTheConfiguredClassLoader() throws Exception {
        ObjectStreamClass descriptor = ObjectStreamClass.lookup(SerializablePayload.class);

        try (ExposedClassLoaderObjectInputStream inputStream = new ExposedClassLoaderObjectInputStream(getClass().getClassLoader())) {
            Class<?> resolvedClass = inputStream.resolveClassFor(descriptor);

            assertThat(resolvedClass).isEqualTo(SerializablePayload.class);
        }
    }

    @Test
    void fallsBackToTheParentResolutionForPrimitiveDescriptors() throws Exception {
        ObjectStreamClass descriptor = ObjectStreamClass.lookupAny(int.class);

        try (ExposedClassLoaderObjectInputStream inputStream = new ExposedClassLoaderObjectInputStream(getClass().getClassLoader())) {
            Class<?> resolvedClass = inputStream.resolveClassFor(descriptor);

            assertThat(resolvedClass).isEqualTo(int.class);
        }
    }

    @Test
    void resolvesProxyClassesWhenInterfacesCanBeProxied() throws Exception {
        try (ExposedClassLoaderObjectInputStream inputStream = new ExposedClassLoaderObjectInputStream(getClass().getClassLoader())) {
            Class<?> proxyClass = inputStream.resolveProxyClassFor(Runnable.class.getName());

            assertThat(Proxy.isProxyClass(proxyClass)).isTrue();
            assertThat(proxyClass.getInterfaces()).containsExactly(Runnable.class);
        }
    }

    @Test
    void fallsBackToTheParentProxyResolutionWhenProxyCreationFails() throws Exception {
        try (ExposedClassLoaderObjectInputStream inputStream = new ExposedClassLoaderObjectInputStream(getClass().getClassLoader())) {
            assertThatThrownBy(() -> inputStream.resolveProxyClassFor(Runnable.class.getName(), Runnable.class.getName()))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    private static byte[] serializationStreamHeader() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.flush();
        }
        return outputStream.toByteArray();
    }

    private static final class ExposedClassLoaderObjectInputStream extends ClassLoaderObjectInputStream {
        private ExposedClassLoaderObjectInputStream(ClassLoader classLoader) throws IOException {
            super(classLoader, new ByteArrayInputStream(serializationStreamHeader()));
        }

        private Class<?> resolveClassFor(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
            return super.resolveClass(objectStreamClass);
        }

        private Class<?> resolveProxyClassFor(String... interfaces) throws IOException, ClassNotFoundException {
            return super.resolveProxyClass(interfaces);
        }
    }

    private static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
