/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

public class SerializedProxyTest {
    @Test
    void restoresProxySerializedThroughWriteReplaceDescriptor() throws Exception {
        ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(SerializedProxyTest.class.getClassLoader());
        try {
            Object proxy = createSerializableProxy();
            byte[] serializedProxy = serializeWithWriteReplace(proxy);

            Object restoredProxy = deserialize(serializedProxy);

            assertThat(ProxyFactory.isProxyClass(restoredProxy.getClass())).isTrue();
            assertThat(restoredProxy).isInstanceOf(GreetingContract.class);
            assertThat(((GreetingContract) restoredProxy).greet("reader")).isEqualTo("handled:reader");
            assertThat(((ProxyObject) restoredProxy).getHandler()).isInstanceOf(SerializableHandler.class);
        } catch (RuntimeException exception) {
            rethrowUnlessUnsupportedDynamicClassLoading(exception);
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextLoader);
        }
    }

    private static Object createSerializableProxy() throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(true);
        factory.setUseWriteReplace(true);
        factory.setInterfaces(new Class[] { GreetingContract.class });
        factory.setFilter(new GreetingMethodFilter());
        return factory.create(new Class[0], new Object[0], new SerializableHandler());
    }

    private static byte[] serializeWithWriteReplace(Object proxy) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(output)) {
            stream.writeObject(proxy);
        }
        return output.toByteArray();
    }

    private static Object deserialize(byte[] serializedProxy) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedProxy);
        try (ObjectInputStream stream = new ObjectInputStream(input)) {
            return stream.readObject();
        }
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof Error error) {
                rethrowUnlessUnsupportedDynamicClassLoading(error);
                return;
            }
            current = current.getCause();
        }
        throw exception;
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface GreetingContract extends Serializable {
        String greet(String name);
    }

    public static final class GreetingMethodFilter implements MethodFilter, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isHandled(Method method) {
            return method.getName().equals("greet");
        }
    }

    public static final class SerializableHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object self, Method method, Method proceed, Object[] arguments) {
            return "handled:" + arguments[0];
        }
    }
}
