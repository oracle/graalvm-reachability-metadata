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
import java.io.Serializable;
import java.lang.reflect.Method;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import javassist.util.proxy.ProxyObjectInputStream;
import javassist.util.proxy.ProxyObjectOutputStream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ProxyObjectInputStreamTest {
    @Test
    void deserializesProxyDescriptorUsingConfiguredClassLoader() throws Exception {
        try {
            Object proxy = createSerializableProxy();
            byte[] serializedProxy = serialize(proxy);

            Object restoredProxy = deserialize(serializedProxy);

            assertThat(ProxyFactory.isProxyClass(restoredProxy.getClass())).isTrue();
            assertThat(restoredProxy).isInstanceOf(GreetingContract.class);
            assertThat(((GreetingContract) restoredProxy).greet("reader")).isEqualTo("handled:reader");
            assertThat(((ProxyObject) restoredProxy).getHandler()).isInstanceOf(SerializableHandler.class);
        } catch (RuntimeException exception) {
            rethrowUnlessUnsupportedDynamicClassLoading(exception);
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        }
    }

    private static Object createSerializableProxy() throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(true);
        factory.setUseWriteReplace(false);
        factory.setInterfaces(new Class[] {GreetingContract.class });
        factory.setFilter(new GreetingMethodFilter());
        return factory.create(new Class[0], new Object[0], new SerializableHandler());
    }

    private static byte[] serialize(Object proxy) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ProxyObjectOutputStream stream = new ProxyObjectOutputStream(output)) {
            stream.writeObject(proxy);
        }
        return output.toByteArray();
    }

    private static Object deserialize(byte[] serializedProxy) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedProxy);
        try (ProxyObjectInputStream stream = new ProxyObjectInputStream(input)) {
            stream.setClassLoader(ProxyObjectInputStreamTest.class.getClassLoader());
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
