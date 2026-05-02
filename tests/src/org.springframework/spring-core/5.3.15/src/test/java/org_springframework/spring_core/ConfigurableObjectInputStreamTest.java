/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.core.Ordered;

public class ConfigurableObjectInputStreamTest {

    @Test
    void resolvesRegularClassWithObjectInputStreamDefaultClassLoader() throws Exception {
        ExposedConfigurableObjectInputStream inputStream = new ExposedConfigurableObjectInputStream();
        ObjectStreamClass classDescriptor = ObjectStreamClass.lookup(String.class);

        Class<?> resolvedClass = inputStream.resolveClassFor(classDescriptor);

        assertThat(resolvedClass).isSameAs(String.class);
    }

    @Test
    void resolvesProxyClassWithObjectInputStreamDefaultClassLoader() throws Exception {
        ExposedConfigurableObjectInputStream inputStream = new ExposedConfigurableObjectInputStream();

        Class<?> proxyClass = inputStream.resolveProxyClassFor(
                Ordered.class.getName(),
                Closeable.class.getName()
        );

        assertThat(Proxy.isProxyClass(proxyClass)).isTrue();
        assertThat(Ordered.class.isAssignableFrom(proxyClass)).isTrue();
        assertThat(Closeable.class.isAssignableFrom(proxyClass)).isTrue();
    }

    private static InputStream serializedStreamHeader() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ObjectOutputStream(output).close();
        return new ByteArrayInputStream(output.toByteArray());
    }

    private static final class ExposedConfigurableObjectInputStream extends ConfigurableObjectInputStream {

        private ExposedConfigurableObjectInputStream() throws IOException {
            super(serializedStreamHeader(), null);
        }

        private Class<?> resolveClassFor(ObjectStreamClass classDescriptor)
                throws IOException, ClassNotFoundException {
            return resolveClass(classDescriptor);
        }

        private Class<?> resolveProxyClassFor(String... interfaces) throws IOException, ClassNotFoundException {
            return resolveProxyClass(interfaces);
        }
    }
}
