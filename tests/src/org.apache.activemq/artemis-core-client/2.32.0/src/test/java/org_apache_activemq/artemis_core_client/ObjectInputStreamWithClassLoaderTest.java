/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_core_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.activemq.artemis.utils.ObjectInputStreamWithClassLoader;
import org.junit.jupiter.api.Test;

public class ObjectInputStreamWithClassLoaderTest {
    @Test
    void readsSerializableObjectWithContextClassLoader() throws Exception {
        SamplePayload payload = new SamplePayload("context-loader");

        Object deserialized = deserializeWithContextClassLoader(
                serialize(payload), ObjectInputStreamWithClassLoaderTest.class.getClassLoader());

        assertThat(deserialized).isEqualTo(payload);
    }

    @Test
    void fallsBackToDefaultObjectInputStreamResolutionWhenContextLoaderCannotLoadClass() throws Exception {
        SamplePayload payload = new SamplePayload("fallback-loader");
        ClassLoader rejectingContextLoader = new RejectingClassLoader(
                ObjectInputStreamWithClassLoaderTest.class.getClassLoader(), SamplePayload.class.getName());

        Object deserialized = deserializeWithContextClassLoader(serialize(payload), rejectingContextLoader);

        assertThat(deserialized).isEqualTo(payload);
    }

    @Test
    void readsSerializedDynamicProxyWithContextClassLoader() throws Exception {
        Greeting proxy = (Greeting) Proxy.newProxyInstance(
                ObjectInputStreamWithClassLoaderTest.class.getClassLoader(),
                new Class<?>[] {Greeting.class},
                new GreetingInvocationHandler());

        Object deserialized = deserializeWithContextClassLoader(
                serialize(proxy), ObjectInputStreamWithClassLoaderTest.class.getClassLoader());

        assertThat(deserialized).isInstanceOf(Greeting.class);
        assertThat(((Greeting) deserialized).greet("Artemis")).isEqualTo("Hello, Artemis");
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
        }
        return buffer.toByteArray();
    }

    private static Object deserializeWithContextClassLoader(byte[] bytes, ClassLoader contextClassLoader)
            throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(contextClassLoader);
        try (ObjectInputStreamWithClassLoader input = new ObjectInputStreamWithClassLoader(
                new ByteArrayInputStream(bytes))) {
            input.setDenyList(null);
            input.setAllowList(ObjectInputStreamWithClassLoader.CATCH_ALL_WILDCARD);
            return input.readObject();
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
    }

    public interface Greeting extends Serializable {
        String greet(String name);
    }

    private static final class GreetingInvocationHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("toString".equals(method.getName())) {
                return "Greeting proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            if ("greet".equals(method.getName())) {
                return "Hello, " + args[0];
            }
            throw new UnsupportedOperationException(method.toString());
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
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

    private static final class SamplePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        private SamplePayload(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SamplePayload)) {
                return false;
            }
            SamplePayload that = (SamplePayload) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
