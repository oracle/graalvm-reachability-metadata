/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.ProxyFactory;

public class CglibAopProxyTest {
    @Test
    void frozenStaticCglibProxyUsesFixedCallbackChain() {
        AtomicInteger invocationCount = new AtomicInteger();
        ProxyFactory proxyFactory = new ProxyFactory(new GreetingTarget());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(countingInterceptor(invocationCount));
        proxyFactory.setFrozen(true);

        GreetingService proxy = (GreetingService) proxyFactory.getProxy();

        assertThat(proxy.greet("Spring")).isEqualTo("Hello Spring");
        assertThat(invocationCount).hasValue(1);
    }

    private static MethodInterceptor countingInterceptor(AtomicInteger invocationCount) {
        return invocation -> {
            invocationCount.incrementAndGet();
            return invocation.proceed();
        };
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class GreetingTarget implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
