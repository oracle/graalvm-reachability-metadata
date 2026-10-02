/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.ProxyFactory;

public class AopProxyUtilsTest {
    @Test
    void jdkProxyAdaptsObjectArrayVarargsToDeclaredArrayType() throws Throwable {
        RecordingVarargsService target = new RecordingVarargsService();
        VarargsService proxy = createJdkProxy(target);
        Method method = VarargsService.class.getMethod("join", String.class, String[].class);
        InvocationHandler invocationHandler = Proxy.getInvocationHandler(proxy);

        Object result = invocationHandler.invoke(proxy, method, new Object[] {", ", new Object[] {"spring", "aop"}});

        assertThat(result).isEqualTo("spring, aop");
        assertThat(target.lastValuesType).isEqualTo(String[].class);
    }

    private static VarargsService createJdkProxy(VarargsService target) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(VarargsService.class);
        return (VarargsService) proxyFactory.getProxy();
    }

    public interface VarargsService {
        String join(String delimiter, String... values);
    }

    public static class RecordingVarargsService implements VarargsService {
        private Class<?> lastValuesType;

        @Override
        public String join(String delimiter, String... values) {
            this.lastValuesType = values.getClass();
            return String.join(delimiter, values);
        }
    }
}
