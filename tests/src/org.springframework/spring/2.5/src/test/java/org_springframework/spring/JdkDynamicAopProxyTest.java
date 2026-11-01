/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdkDynamicAopProxyTest {
    @Test
    void invokesInterfaceProxyAndAdvice() {
        ProxyFactory factory = new ProxyFactory(new TestBeans.GreetingService());
        AtomicInteger calls = new AtomicInteger();
        factory.addAdvice((MethodInterceptor) invocation -> {
            calls.incrementAndGet();
            return invocation.proceed();
        });
        TestBeans.Greeting proxy = (TestBeans.Greeting) factory.getProxy();
        assertTrue(AopUtils.isJdkDynamicProxy(proxy));
        assertEquals("Hello visitor", proxy.greet("visitor"));
        assertEquals(1, calls.get());
        assertEquals(proxy, proxy);
    }
}
