/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.util.ByteArrayInputStream;
import org.apache.activemq.util.ByteSequence;
import org.apache.activemq.util.ClassLoadingAwareObjectInputStream;
import org.apache.activemq.wireformat.ObjectStreamWireFormat;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoadingAwareObjectInputStreamTest {

    @Test
    void resolvesLibraryTypesWhileUnmarshalling() throws Exception {
        ObjectStreamWireFormat wireFormat = new ObjectStreamWireFormat();

        ActiveMQQueue decoded = (ActiveMQQueue) wireFormat.unmarshal(
                wireFormat.marshal(new ActiveMQQueue("serialized.queue")));

        assertThat(decoded.getPhysicalName()).isEqualTo("serialized.queue");
    }

    @Test
    void fallsBackToTheLibraryClassLoaderWhileUnmarshalling() throws Exception {
        ObjectStreamWireFormat wireFormat = new ObjectStreamWireFormat();
        ByteSequence encoded = wireFormat.marshal(new ActiveMQQueue("fallback.queue"));
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            ActiveMQQueue decoded = (ActiveMQQueue) wireFormat.unmarshal(input);

            assertThat(decoded.getPhysicalName()).isEqualTo("fallback.queue");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void restoresProxyWithTheThreadContextClassLoader() throws Exception {
        Greeting restored = restoreProxy(ClassLoadingAwareObjectInputStreamTest.class.getClassLoader());

        assertThat(restored.greet("context loader")).isEqualTo("Hello, context loader");
    }

    @Test
    void restoresProxyWithTheInputClassLoader() throws Exception {
        Greeting restored = restoreProxy(null);

        assertThat(restored.greet("input loader")).isEqualTo("Hello, input loader");
    }

    private static Greeting restoreProxy(ClassLoader contextClassLoader) throws Exception {
        Greeting original = (Greeting) Proxy.newProxyInstance(
                ClassLoadingAwareObjectInputStreamTest.class.getClassLoader(),
                new Class<?>[] {Greeting.class},
                new GreetingHandler("Hello"));
        ByteSequence encoded = new ObjectStreamWireFormat().marshal(original);
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try (ClassLoadingAwareObjectInputStream input =
                new ClassLoadingAwareObjectInputStream(new ByteArrayInputStream(encoded))) {
            input.setTrustAllPackages(true);
            thread.setContextClassLoader(contextClassLoader);
            return (Greeting) input.readObject();
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    public interface Greeting extends Serializable {
        String greet(String name);
    }

    public static final class GreetingHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        private final String greeting;

        public GreetingHandler(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if ("greet".equals(method.getName())) {
                return greeting + ", " + arguments[0];
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
