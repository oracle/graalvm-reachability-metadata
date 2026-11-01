/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.NameMatchMethodPointcut;

import static org.junit.jupiter.api.Assertions.*;

public class AopUtilsTest {
    @Test
    void matchesPublicMethodsAndInvokesUnadvisedJoinpoint() {
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("greet");
        assertTrue(AopUtils.canApply(pointcut, TestBeans.GreetingService.class));
        pointcut.setMappedName("absent");
        assertFalse(AopUtils.canApply(pointcut, TestBeans.GreetingService.class));
        ProxyFactory factory = new ProxyFactory(new TestBeans.GreetingService());
        TestBeans.Greeting proxy = (TestBeans.Greeting) factory.getProxy();
        assertEquals("Hello customer", proxy.greet("customer"));
    }
}
