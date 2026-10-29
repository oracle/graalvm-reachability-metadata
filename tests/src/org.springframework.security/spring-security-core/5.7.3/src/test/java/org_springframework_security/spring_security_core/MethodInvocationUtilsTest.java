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
    void describesInvocationOnConcreteService() {
        ReportService service = new ReportService();

        MethodInvocation invocation = MethodInvocationUtils.create(service, "findReport", "monthly");

        assertNotNull(invocation);
        assertSame(service, invocation.getThis());
        assertEquals("findReport", invocation.getMethod().getName());
        assertEquals(ReportService.class, invocation.getMethod().getDeclaringClass());
        assertArrayEquals(new Object[]{"monthly"}, invocation.getArguments());
        assertArrayEquals(new Class<?>[]{String.class}, invocation.getMethod().getParameterTypes());
    }

    @Test
    void resolvesMethodDeclaredOnJdkProxyInterface() {
        ProxyFactory factory = new ProxyFactory(new ReportService());
        Reports proxy = (Reports) factory.getProxy();

        MethodInvocation invocation = MethodInvocationUtils.create(proxy, "findReport", "weekly");

        assertNotNull(invocation);
        assertSame(proxy, invocation.getThis());
        assertEquals(Reports.class, invocation.getMethod().getDeclaringClass());
        assertArrayEquals(new Object[]{"weekly"}, invocation.getArguments());
        assertEquals("report:weekly", proxy.findReport("weekly"));
    }

    @Test
    void findsUniqueParameterizedMethodByName() {
        MethodInvocation invocation = MethodInvocationUtils.createFromClass(ReportService.class, "findReport");

        assertNotNull(invocation);
        assertEquals("findReport", invocation.getMethod().getName());
        assertEquals(String.class, invocation.getMethod().getReturnType());
        assertArrayEquals(new Class<?>[]{String.class}, invocation.getMethod().getParameterTypes());
    }

    public interface Reports {
        String findReport(String name);
    }

    public static class ReportService implements Reports {
        @Override
        public String findReport(String name) {
            return "report:" + name;
        }
    }
}
