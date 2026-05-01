/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import javassist.util.proxy.ProxyObjectOutputStream;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyFactoryTest {
    @Test
    void createInstantiatesProxyAndSerializesProxyDescriptor() throws Throwable {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(false);
        factory.setUseWriteReplace(false);
        factory.setSuperclass(ProxyTarget.class);
        factory.setFilter(new MethodFilter() {
            @Override
            public boolean isHandled(Method method) {
                return method.getName().equals("echo") || method.getName().equals("sum");
            }
        });

        try {
            ProxyTarget proxy = (ProxyTarget) factory.create(
                    new Class[] { String.class },
                    new Object[] { "constructed" },
                    new PrefixingHandler());

            assertThat(proxy).isInstanceOf(ProxyObject.class);
            assertThat(ProxyFactory.isProxyClass(proxy.getClass())).isTrue();
            assertThat(((ProxyObject) proxy).getHandler()).isInstanceOf(PrefixingHandler.class);
            assertThat(proxy.value()).isEqualTo("constructed");
            assertThat(proxy.echo("input")).isEqualTo("handled:super:input");
            assertThat(proxy.sum(2, 5)).isEqualTo(7);
            assertThat(serializeWithProxyDescriptor(proxy)).isNotEmpty();
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    private static byte[] serializeWithProxyDescriptor(ProxyTarget proxy) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ProxyObjectOutputStream output = new ProxyObjectOutputStream(bytes)) {
            output.writeObject(proxy);
        }
        return bytes.toByteArray();
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static class ProxyTarget implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public ProxyTarget(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public String echo(String input) {
            return "super:" + input;
        }

        public int sum(int left, int right) {
            return left + right;
        }
    }

    private static class PrefixingHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
            Object result = proceed.invoke(self, args);
            if (thisMethod.getName().equals("echo")) {
                return "handled:" + result;
            }
            return result;
        }
    }
}
