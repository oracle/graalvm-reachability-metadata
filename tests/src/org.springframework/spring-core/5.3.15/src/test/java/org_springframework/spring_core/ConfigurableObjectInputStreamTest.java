/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.util.SerializationUtils;

/** Verifies class and proxy deserialization through configurable resolution paths. */
public class ConfigurableObjectInputStreamTest {
    @Test
    void resolvesSerializedClassWithConfiguredLoader() throws Exception {
        Payload payload = new Payload("spring");

        assertThat(deserialize(SerializationUtils.serialize(payload), getClass().getClassLoader()))
                .isEqualTo(payload);
    }

    @Test
    void resolvesSerializedClassWithDefaultLoader() throws Exception {
        Payload payload = new Payload("default");

        assertThat(deserialize(SerializationUtils.serialize(payload), null)).isEqualTo(payload);
    }

    @Test
    void resolvesSerializedProxyWithDefaultLoader() throws Exception {
        Greeting greeting = (Greeting) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {Greeting.class}, new GreetingHandler());

        Greeting restored = (Greeting) deserialize(SerializationUtils.serialize(greeting), null);

        assertThat(restored.greet("Spring")).isEqualTo("Hello Spring");
    }

    private static Object deserialize(byte[] bytes, ClassLoader classLoader) throws Exception {
        try (ConfigurableObjectInputStream input = new ConfigurableObjectInputStream(
                new ByteArrayInputStream(bytes), classLoader)) {
            return input.readObject();
        }
    }

    public interface Greeting {
        String greet(String name);
    }

    private static final class GreetingHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return "Hello " + arguments[0];
        }
    }

    private static final class Payload implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String value;

        private Payload(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Payload && value.equals(((Payload) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
