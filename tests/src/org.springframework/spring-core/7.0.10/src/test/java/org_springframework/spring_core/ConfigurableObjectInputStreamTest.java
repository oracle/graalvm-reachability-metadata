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
import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.core.Ordered;

/** Verifies deserialization through the stream's default class resolution path. */
public class ConfigurableObjectInputStreamTest {
    @Test
    void resolvesSerializedClassWithDefaultClassLoader() throws Exception {
        SerializableFixture original = new SerializableFixture("spring");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(output)) {
            stream.writeObject(original);
        }

        Object restored;
        try (ConfigurableObjectInputStream stream = new ConfigurableObjectInputStream(
                new ByteArrayInputStream(output.toByteArray()), null)) {
            restored = stream.readObject();
        }

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void resolvesProxyClassWithDefaultClassLoader() throws Exception {
        Class<?> proxyClass;
        try (ExposedConfigurableObjectInputStream stream =
                new ExposedConfigurableObjectInputStream()) {
            proxyClass = stream.resolveProxyClassFor(
                    Ordered.class.getName(), Closeable.class.getName());
        }

        assertThat(Proxy.isProxyClass(proxyClass)).isTrue();
        assertThat(Ordered.class.isAssignableFrom(proxyClass)).isTrue();
        assertThat(Closeable.class.isAssignableFrom(proxyClass)).isTrue();
    }

    private static InputStream serializedStreamHeader() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(output)) {
            stream.flush();
        }
        return new ByteArrayInputStream(output.toByteArray());
    }

    private static final class ExposedConfigurableObjectInputStream
            extends ConfigurableObjectInputStream {
        private ExposedConfigurableObjectInputStream() throws IOException {
            super(serializedStreamHeader(), null);
        }

        private Class<?> resolveProxyClassFor(String... interfaces)
                throws IOException, ClassNotFoundException {
            return resolveProxyClass(interfaces);
        }
    }

    public static final class SerializableFixture implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public SerializableFixture(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SerializableFixture
                    && this.value.equals(((SerializableFixture) other).value);
        }

        @Override
        public int hashCode() {
            return this.value.hashCode();
        }
    }
}
