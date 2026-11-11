/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.util.MethodInvocationUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MethodInvocationUtilsTest {
    @Test
    void describesAProxiedInterfaceMethodAndItsArguments() {
        ProxyFactory factory = new ProxyFactory(new GreetingService());
        factory.setInterfaces(Greeting.class);
        Greeting proxy = (Greeting) factory.getProxy();
        MethodInvocation invocation = MethodInvocationUtils.create(proxy, "greet", "Ada");

        assertNotNull(invocation);
        assertSame(proxy, invocation.getThis());
        assertEquals(Greeting.class, invocation.getMethod().getDeclaringClass());
        assertArrayEquals(new Object[]{"Ada"}, invocation.getArguments());
        assertEquals("Hello Ada", proxy.greet("Ada"));
    }

    @Test
    void discoversAUniqueMethodWithoutItsParameterTypes() {
        MethodInvocation invocation = MethodInvocationUtils.createFromClass(GreetingService.class, "greet");

        assertNotNull(invocation);
        assertEquals("greet", invocation.getMethod().getName());
        assertArrayEquals(new Class<?>[]{String.class}, invocation.getMethod().getParameterTypes());
        assertEquals(String.class, invocation.getMethod().getReturnType());
    }

    public interface Greeting {
        String greet(String name);
    }

    public static final class GreetingService implements Greeting {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
