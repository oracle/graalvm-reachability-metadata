/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.aop.ProxyUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyUtilTest {

    @Test
    void createsJdkProxyWithExplicitClassLoaderAndInterfaces() {
        RecordingInvocationHandler handler = new RecordingInvocationHandler();

        GreetingService proxy = ProxyUtil.newProxyInstance(
                GreetingService.class.getClassLoader(), handler, GreetingService.class, Labelled.class);

        assertThat(proxy.greet("Ada")).isEqualTo("Hello Ada");
        assertThat(((Labelled) proxy).label()).isEqualTo("proxied-service");
        assertThat(proxy).isInstanceOf(GreetingService.class).isInstanceOf(Labelled.class);
        assertThat(handler.invokedMethods()).containsExactly("greet", "label");
    }

    public interface GreetingService {
        String greet(String name);
    }

    public interface Labelled {
        String label();
    }

    private static final class RecordingInvocationHandler implements InvocationHandler {
        private final List<String> invokedMethods = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }

            invokedMethods.add(method.getName());
            if ("greet".equals(method.getName())) {
                return "Hello " + args[0];
            }
            if ("label".equals(method.getName())) {
                return "proxied-service";
            }
            throw new AssertionError("Unexpected proxy method: " + method);
        }

        List<String> invokedMethods() {
            return invokedMethods;
        }

        private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            if ("toString".equals(method.getName())) {
                return "ProxyUtilTest proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            throw new AssertionError("Unexpected Object method: " + method);
        }
    }
}
