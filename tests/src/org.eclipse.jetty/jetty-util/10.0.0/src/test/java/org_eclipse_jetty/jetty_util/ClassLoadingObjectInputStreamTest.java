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
import java.util.function.IntFunction;

import org.eclipse.jetty.util.ClassLoadingObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassLoadingObjectInputStreamTest {
    @Test
    void readsObjectsWithAnExplicitLoaderAndResolvesPrimitiveClasses() throws Exception {
        byte[] bytes = serialize(new Endpoint("localhost", 8080), int.class);
        try (ClassLoadingObjectInputStream input = new ClassLoadingObjectInputStream(new ByteArrayInputStream(bytes))) {
            Endpoint endpoint = (Endpoint) input.readObject(getClass().getClassLoader());
            assertEquals("localhost", endpoint.host);
            assertEquals(8080, endpoint.port);
            assertSame(int.class, input.readObject(getClass().getClassLoader()));
        }
    }

    @Test
    void readsObjectsUsingTheThreadContextLoader() throws Exception {
        byte[] bytes = serialize(new Endpoint("example.com", 8443));
        try (ClassLoadingObjectInputStream input = new ClassLoadingObjectInputStream(new ByteArrayInputStream(bytes))) {
            Endpoint endpoint = (Endpoint) input.readObject();
            assertEquals("example.com", endpoint.host);
            assertEquals(8443, endpoint.port);
        }
    }

    @Test
    void restoresSerializableProxyBehavior() throws Exception {
        IntFunction<?> greeting = (IntFunction<?>) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IntFunction.class}, new GreetingHandler());
        byte[] bytes = serialize(greeting);
        try (ClassLoadingObjectInputStream input = new ClassLoadingObjectInputStream(new ByteArrayInputStream(bytes))) {
            IntFunction<?> restored = (IntFunction<?>) input.readObject(getClass().getClassLoader());
            assertEquals("Hello, 42", restored.apply(42));
        }
    }

    private static byte[] serialize(Object... values) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            for (Object value : values) {
                output.writeObject(value);
            }
        }
        return bytes.toByteArray();
    }

    public static class Endpoint implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String host;
        private final int port;

        public Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    public static class GreetingHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return "Hello, " + args[0];
        }
    }
}
