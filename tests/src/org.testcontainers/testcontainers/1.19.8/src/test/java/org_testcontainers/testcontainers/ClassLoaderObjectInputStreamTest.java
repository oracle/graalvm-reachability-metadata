/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;
import org.testcontainers.shaded.com.google.common.reflect.Reflection;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderObjectInputStreamTest {
    @Test
    void resolvesSerializedClassesWithTheProvidedLoader() throws Exception {
        byte[] encoded = SerializationUtils.serialize(new ArrayList<>(List.of("value")));

        try (
            ClassLoaderObjectInputStream input = new ClassLoaderObjectInputStream(
                new DenyingClassLoader(),
                new ByteArrayInputStream(encoded)
            )
        ) {
            assertThat(input.readObject()).isEqualTo(List.of("value"));
        }
    }

    @Test
    void resolvesSerializedProxyInterfacesWithTheProvidedLoader() throws Exception {
        Greeting greeting = Reflection.newProxy(Greeting.class, new GreetingHandler());
        byte[] encoded = SerializationUtils.serialize(greeting);

        try (
            ClassLoaderObjectInputStream input = new ClassLoaderObjectInputStream(
                new DelegatingClassLoader(),
                new ByteArrayInputStream(encoded)
            )
        ) {
            assertThat(((Greeting) input.readObject()).greet("proxy")).isEqualTo("Hello proxy");
        }
    }

    public static class DenyingClassLoader extends ClassLoader {
        public DenyingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }

    public static class DelegatingClassLoader extends ClassLoader {
        public DelegatingClassLoader() {
            super(ClassLoaderObjectInputStreamTest.class.getClassLoader());
        }
    }

    interface Greeting extends Serializable {
        String greet(String name);
    }

    public static class GreetingHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments) {
            return "Hello " + arguments[0];
        }
    }
}
