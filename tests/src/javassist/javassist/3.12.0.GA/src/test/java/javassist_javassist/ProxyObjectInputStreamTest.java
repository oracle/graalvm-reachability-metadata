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

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyObjectInputStreamTest {
    @Test
    void readsProxyDescriptorAndRecreatesProxyClass() throws Throwable {
        try {
            NamedGreeter proxy = createProxy();
            byte[] serializedProxy = serialize(proxy);

            NamedGreeter deserialized = deserialize(serializedProxy);

            assertThat(deserialized).isInstanceOf(ProxyObject.class);
            assertThat(deserialized).isInstanceOf(Greeter.class);
            assertThat(ProxyFactory.isProxyClass(deserialized.getClass())).isTrue();
            assertThat(deserialized.greet("reader")).isEqualTo("handled:hello reader");
            assertThat(deserialized.identity()).isEqualTo("greeter");
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    private static NamedGreeter createProxy() throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(true);
        factory.setUseWriteReplace(false);
        factory.setSuperclass(NamedGreeter.class);
        factory.setInterfaces(new Class[] { Greeter.class });
        factory.setFilter(new GreeterMethodFilter());

        NamedGreeter proxy = (NamedGreeter) factory.create(new Class[0], new Object[0], new GreetingHandler());
        return proxy;
    }

    private static byte[] serialize(NamedGreeter proxy) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ProxyObjectOutputStream output = new ProxyObjectOutputStream(bytes)) {
            output.writeObject(proxy);
        }
        return bytes.toByteArray();
    }

    private static NamedGreeter deserialize(byte[] serializedProxy) throws IOException, ClassNotFoundException {
        try (ProxyObjectInputStream input = new ProxyObjectInputStream(new ByteArrayInputStream(serializedProxy))) {
            input.setClassLoader(ProxyObjectInputStreamTest.class.getClassLoader());
            return (NamedGreeter) input.readObject();
        }
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface Greeter {
        String greet(String name);
    }

    public static class NamedGreeter implements Serializable {
        private static final long serialVersionUID = 1L;

        public String greet(String name) {
            return "hello " + name;
        }

        public String identity() {
            return "greeter";
        }
    }

    private static final class GreeterMethodFilter implements MethodFilter {
        @Override
        public boolean isHandled(Method method) {
            return method.getName().equals("greet");
        }
    }

    private static final class GreetingHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
            return "handled:" + proceed.invoke(self, args);
        }
    }
}
