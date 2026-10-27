/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.jetty.util.ClassLoadingObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoadingObjectInputStreamTest {
    @Test
    void deserializesAProxyAndItsHandlerWithClassLoadingFallback() throws Exception {
        Greeting greeting = (Greeting) Proxy.newProxyInstance(
                ClassLoadingObjectInputStreamTest.class.getClassLoader(),
                new Class<?>[] {Greeting.class},
                new GreetingHandler());
        byte[] serialized = serialize(greeting);

        try (ClassLoadingObjectInputStream input =
                new ClassLoadingObjectInputStream(new ByteArrayInputStream(serialized))) {
            Greeting restored = (Greeting) input.readObject(null);
            assertThat(restored.greet("Jetty")).isEqualTo("Hello, Jetty");
        }
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    public interface Greeting extends Serializable {
        String greet(String name);
    }

    public static final class GreetingHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if ("greet".equals(method.getName())) {
                return "Hello, " + arguments[0];
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
