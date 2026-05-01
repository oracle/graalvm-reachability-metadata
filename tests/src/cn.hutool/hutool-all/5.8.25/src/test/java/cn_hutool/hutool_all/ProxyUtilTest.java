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

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyUtilTest {
    @Test
    public void createsJdkProxyWithDefaultClassLoader() {
        RecordingInvocationHandler handler = new RecordingInvocationHandler("Hello");

        GreetingService proxy = ProxyUtil.newProxyInstance(handler, GreetingService.class);

        assertThat(proxy.greet("Hutool")).isEqualTo("Hello, Hutool");
        assertThat(handler.getLastMethodName()).isEqualTo("greet");
        assertThat(proxy.description()).isEqualTo("proxy:description");
    }

    @Test
    public void createsJdkProxyWithExplicitClassLoader() {
        RecordingInvocationHandler handler = new RecordingInvocationHandler("Hi");
        ClassLoader classLoader = ProxyUtilTest.class.getClassLoader();

        GreetingService proxy = ProxyUtil.newProxyInstance(classLoader, handler, GreetingService.class);

        assertThat(proxy.greet("ProxyUtil")).isEqualTo("Hi, ProxyUtil");
        assertThat(handler.getLastMethodName()).isEqualTo("greet");
    }

    public interface GreetingService {
        String greet(String name);

        String description();
    }

    public static class RecordingInvocationHandler implements InvocationHandler {
        private final String salutation;
        private String lastMethodName;

        public RecordingInvocationHandler(String salutation) {
            this.salutation = salutation;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            this.lastMethodName = method.getName();
            if ("greet".equals(method.getName())) {
                return salutation + ", " + args[0];
            }
            if ("description".equals(method.getName())) {
                return "proxy:description";
            }
            throw new UnsupportedOperationException(method.getName());
        }

        public String getLastMethodName() {
            return lastMethodName;
        }
    }
}
