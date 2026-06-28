/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.activemq.util.ClassLoadingAwareObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoadingAwareObjectInputStreamTest {

    @Test
    void readObjectResolvesSerializablePayloadWithContextClassLoader() throws Exception {
        Payload payload = new Payload("orders", 3);

        Object restored = deserialize(serialize(payload));

        assertThat(restored).isEqualTo(payload);
    }

    @Test
    void readObjectResolvesSerializablePayloadWithFallbackClassLoader() throws Exception {
        Payload payload = new Payload("fallback", 7);
        byte[] bytes = serialize(payload);

        Object restored = deserializeWithContextLoader(bytes, ClassLoader.getPlatformClassLoader());

        assertThat(restored).isEqualTo(payload);
    }

    @Test
    void readObjectResolvesProxyClassWithContextClassLoader() throws Exception {
        Echo echo = newEchoProxy("context");

        Echo restored = (Echo) deserialize(serialize(echo));

        assertThat(restored.echo("message")).isEqualTo("context:message");
    }

    @Test
    void readObjectResolvesProxyClassWithFallbackClassLoader() throws Exception {
        Echo echo = newEchoProxy("fallback");
        byte[] bytes = serialize(echo);

        Echo restored = (Echo) deserializeWithContextLoader(bytes, ClassLoader.getPlatformClassLoader());

        assertThat(restored.echo("message")).isEqualTo("fallback:message");
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ClassLoadingAwareObjectInputStream input = new ClassLoadingAwareObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            input.setTrustAllPackages(true);
            return input.readObject();
        }
    }

    private static Object deserializeWithContextLoader(byte[] bytes, ClassLoader contextLoader)
            throws IOException, ClassNotFoundException {
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(contextLoader);
        try {
            return deserialize(bytes);
        } finally {
            thread.setContextClassLoader(previousLoader);
        }
    }

    private static Echo newEchoProxy(String prefix) {
        return Echo.class.cast(Proxy.newProxyInstance(
                Echo.class.getClassLoader(),
                new Class<?>[] {Echo.class},
                new EchoHandler(prefix)));
    }

    public interface Echo extends Serializable {
        String echo(String value);
    }

    public static final class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int count;

        Payload(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Payload)) {
                return false;
            }
            Payload payload = (Payload) other;
            return count == payload.count && name.equals(payload.name);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + count;
            return result;
        }
    }

    public static final class EchoHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        private final String prefix;

        EchoHandler(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("echo".equals(method.getName())) {
                return prefix + ":" + args[0];
            }
            if ("toString".equals(method.getName())) {
                return "EchoProxy[" + prefix + "]";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
