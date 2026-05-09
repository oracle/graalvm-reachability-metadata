/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_osgi_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObjectOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ProxyFactoryTest {
    @Test
    void createsProxyInstanceAndSerializesProxyClassDescriptor() throws Exception {
        try {
            SampleService proxy = createProxy();

            assertThat(ProxyFactory.isProxyClass(proxy.getClass())).isTrue();
            assertThat(proxy.name()).isEqualTo("delegate");
            assertThat(proxy.greet("Weld")).isEqualTo("proxy:Weld");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ProxyObjectOutputStream objectOutputStream = new ProxyObjectOutputStream(outputStream)) {
                objectOutputStream.writeObject(proxy);
            }

            assertThat(outputStream.toByteArray()).isNotEmpty();
        } catch (RuntimeException exception) {
            rethrowUnlessCausedByUnsupportedFeatureError(exception);
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static void rethrowUnlessCausedByUnsupportedFeatureError(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            cause = cause.getCause();
        }
        throw exception;
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static SampleService createProxy() throws Exception {
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setSuperclass(SampleService.class);
        proxyFactory.setUseWriteReplace(false);
        proxyFactory.setFilter(method -> "greet".equals(method.getName()));
        proxyFactory.setHandler(new GreetingHandler());

        return (SampleService) proxyFactory.create(
                new Class[] { String.class },
                new Object[] { "delegate" });
    }

    public static class SampleService implements Serializable {
        private final String name;

        public SampleService() {
            this("default");
        }

        public SampleService(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public String greet(String value) {
            return "service:" + value;
        }
    }

    private static final class GreetingHandler implements MethodHandler, Serializable {
        @Override
        public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) {
            return "proxy:" + args[0];
        }
    }
}
