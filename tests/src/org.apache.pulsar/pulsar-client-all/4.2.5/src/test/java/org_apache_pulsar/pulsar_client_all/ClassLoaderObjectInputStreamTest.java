/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.apache.pulsar.shade.com.google.common.reflect.Reflection;
import org.apache.pulsar.shade.org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.junit.jupiter.api.Test;

public class ClassLoaderObjectInputStreamTest {
    @Test
    void resolvesSerializableClassesAndProxyInterfacesWithProvidedLoader() throws Exception {
        Greeting proxy = Reflection.newProxy(Greeting.class, new GreetingHandler());
        byte[] bytes = serialize(new Message("payload"), proxy);

        try (ClassLoaderObjectInputStream input = new ClassLoaderObjectInputStream(
                getClass().getClassLoader(), new ByteArrayInputStream(bytes))) {
            Message message = (Message) input.readObject();
            Greeting restoredProxy = (Greeting) input.readObject();

            assertThat(message.value).isEqualTo("payload");
            assertThat(restoredProxy.greet("Pulsar")).isEqualTo("hello Pulsar");
        }
    }

    @Test
    void fallsBackToDefaultLoaderForSerializableClass() throws Exception {
        byte[] bytes = serialize(new Message("fallback"));
        ClassLoader loader = new RejectingClassLoader(
                getClass().getClassLoader(), Message.class.getName());

        try (ClassLoaderObjectInputStream input = new ClassLoaderObjectInputStream(
                loader, new ByteArrayInputStream(bytes))) {
            Message restored = (Message) input.readObject();

            assertThat(restored.value).isEqualTo("fallback");
        }
    }

    @Test
    void fallsBackToDefaultLoaderForNonPublicProxyInterface() throws Exception {
        HiddenGreeting proxy = Reflection.newProxy(HiddenGreeting.class, new GreetingHandler());
        byte[] bytes = serialize(proxy);
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
        };

        try (ClassLoaderObjectInputStream input = new ClassLoaderObjectInputStream(
                loader, new ByteArrayInputStream(bytes))) {
            HiddenGreeting restored = (HiddenGreeting) input.readObject();

            assertThat(restored.greet("Pulsar")).isEqualTo("hello Pulsar");
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

    public interface Greeting extends Serializable {
        String greet(String name);
    }

    interface HiddenGreeting extends Serializable {
        String greet(String name);
    }

    public static class Message implements Serializable {
        private final String value;

        public Message(String value) {
            this.value = value;
        }
    }

    public static class GreetingHandler implements InvocationHandler, Serializable {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return "hello " + arguments[0];
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClass;

        private RejectingClassLoader(ClassLoader parent, String rejectedClass) {
            super(parent);
            this.rejectedClass = rejectedClass;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClass.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
