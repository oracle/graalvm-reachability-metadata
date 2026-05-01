/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializedProxyTest {
    @Test
    void deserializesWriteReplaceProxyThroughSerializedProxy() throws Throwable {
        try {
            SerializableCounter proxy = createWriteReplaceProxy();
            byte[] serializedProxy = serializeWithObjectStream(proxy);

            SerializableCounter deserialized = deserializeWithObjectStream(serializedProxy);

            assertThat(deserialized).isInstanceOf(ProxyObject.class);
            assertThat(deserialized).isInstanceOf(Counter.class);
            assertThat(ProxyFactory.isProxyClass(deserialized.getClass())).isTrue();
            assertThat(deserialized.increment(4)).isEqualTo(15);
            assertThat(deserialized.identity()).isEqualTo("counter");
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    private static SerializableCounter createWriteReplaceProxy() throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(true);
        factory.setUseWriteReplace(true);
        factory.setSuperclass(SerializableCounter.class);
        factory.setInterfaces(new Class[] { Counter.class });
        factory.setFilter(new IncrementMethodFilter());

        return (SerializableCounter) factory.create(new Class[0], new Object[0], new IncrementHandler());
    }

    private static byte[] serializeWithObjectStream(SerializableCounter proxy) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(proxy);
        }
        return bytes.toByteArray();
    }

    private static SerializableCounter deserializeWithObjectStream(byte[] serializedProxy)
            throws IOException, ClassNotFoundException {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(SerializedProxyTest.class.getClassLoader());
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedProxy))) {
            return (SerializableCounter) input.readObject();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface Counter {
        int increment(int value);
    }

    public static class SerializableCounter implements Counter, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int increment(int value) {
            return value + 1;
        }

        public String identity() {
            return "counter";
        }
    }

    private static final class IncrementMethodFilter implements MethodFilter {
        @Override
        public boolean isHandled(Method method) {
            return method.getName().equals("increment");
        }
    }

    private static final class IncrementHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
            Integer originalValue = (Integer) proceed.invoke(self, args);
            return originalValue + 10;
        }
    }
}
