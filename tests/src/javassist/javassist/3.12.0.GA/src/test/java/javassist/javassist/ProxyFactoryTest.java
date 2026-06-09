/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import javassist.util.proxy.ProxyObjectOutputStream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ProxyFactoryTest {
    @Test
    void createsProxyInstanceWithConstructorAndInvocationHandler() throws Exception {
        try {
            ProxyFactory factory = new ProxyFactory();
            factory.setUseCache(false);
            factory.setSuperclass(GreetingService.class);
            factory.setFilter(method -> method.getName().equals("message"));

            GreetingService proxy = (GreetingService) factory.create(
                    new Class[] { String.class },
                    new Object[] { "original" },
                    (self, method, proceed, arguments) -> "handled:" + proceed.invoke(self, arguments));

            assertThat(ProxyFactory.isProxyClass(proxy.getClass())).isTrue();
            assertThat(proxy.message()).isEqualTo("handled:original");
        } catch (RuntimeException exception) {
            if (!hasUnsupportedFeatureError(exception)) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Test
    void serializesProxyObjectDescriptorWithFilterSignature() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ProxyObjectOutputStream stream = new ProxyObjectOutputStream(output)) {
            stream.writeObject(new SerializableProxyFixture());
        }

        assertThat(output.toByteArray()).isNotEmpty();
    }

    public static class GreetingService {
        private final String value;

        public GreetingService(String value) {
            this.value = value;
        }

        public String message() {
            return value;
        }
    }

    public static class SerializableProxyFixture implements ProxyObject, Serializable {
        private static final long serialVersionUID = 1L;

        public static byte[] _filter_signature = new byte[] { 1, 2, 3 };

        private transient MethodHandler handler = new RecordingMethodHandler();

        @Override
        public void setHandler(MethodHandler methodHandler) {
            handler = methodHandler;
        }

        @Override
        public MethodHandler getHandler() {
            return handler;
        }
    }

    public static class RecordingMethodHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object self, Method method, Method proceed, Object[] arguments) throws Throwable {
            return proceed == null ? null : proceed.invoke(self, arguments);
        }
    }
}
