/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu_inject.cglib;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ProxyTest {

    @Test
    void createsProxyThatDispatchesInterfaceMethodsToInvocationHandler() {
        try {
            GreetingInvocationHandler handler = new GreetingInvocationHandler();
            Greeting greeting = (Greeting) Proxy.newProxyInstance(
                    ProxyTest.class.getClassLoader(), new Class<?>[]{Greeting.class}, handler);

            assertThat(greeting.greet("Ada")).isEqualTo("hello Ada");
            assertThat(Proxy.isProxyClass(greeting.getClass())).isTrue();
            assertThat(Proxy.getInvocationHandler(greeting)).isSameAs(handler);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public interface Greeting {
        String greet(String name);
    }

    public static final class GreetingInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return "hello " + arguments[0];
        }
    }
}
