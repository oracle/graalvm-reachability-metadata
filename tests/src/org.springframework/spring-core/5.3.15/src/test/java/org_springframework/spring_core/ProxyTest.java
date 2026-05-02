/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.proxy.InvocationHandler;
import org.springframework.cglib.proxy.Proxy;

public class ProxyTest {

    @Test
    void createsProxyInstanceForInterface() {
        InvocationHandler handler = (object, method, arguments) -> {
            if ("toString".equals(method.getName())) {
                return "proxy greeting";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(object);
            }
            if ("equals".equals(method.getName())) {
                return object == arguments[0];
            }
            if ("greet".equals(method.getName())) {
                return "hello " + arguments[0];
            }
            throw new UnsupportedOperationException(method.toString());
        };

        try {
            Object proxy = Proxy.newProxyInstance(
                    Greeting.class.getClassLoader(),
                    new Class<?>[] {Greeting.class},
                    handler
            );

            assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
            assertThat(proxy).isInstanceOf(Greeting.class);
            assertThat(((Greeting) proxy).greet("Spring")).isEqualTo("hello Spring");
            assertThat(Proxy.getInvocationHandler(proxy)).isSameAs(handler);
        }
        catch (CodeGenerationException ex) {
            ignoreUnsupportedDynamicClassLoading(ex);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static void ignoreUnsupportedDynamicClassLoading(CodeGenerationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
            return;
        }
        throw ex;
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface Greeting {

        String greet(String name);
    }
}
