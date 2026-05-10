/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_orm;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.orm.hibernate5.HibernateTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HibernateTemplateInnerCloseSuppressingInvocationHandlerTest {
    @Test
    void closeSuppressingProxyDelegatesSessionMethodsToTarget() {
        RecordingSessionInvocationHandler targetHandler = new RecordingSessionInvocationHandler();
        Session targetSession = newSessionProxy(targetHandler);

        TestHibernateTemplate template = new TestHibernateTemplate();
        Session proxiedSession = template.createCloseSuppressingProxy(targetSession);

        assertNotSame(targetSession, proxiedSession);
        assertTrue(proxiedSession.isOpen());
        assertEquals(1, targetHandler.invocationCount());
        assertEquals("isOpen", targetHandler.lastInvokedMethodName());
    }

    @Test
    void closeSuppressingProxySuppressesCloseCalls() {
        RecordingSessionInvocationHandler targetHandler = new RecordingSessionInvocationHandler();
        Session targetSession = newSessionProxy(targetHandler);

        TestHibernateTemplate template = new TestHibernateTemplate();
        Session proxiedSession = template.createCloseSuppressingProxy(targetSession);
        proxiedSession.close();

        assertEquals(0, targetHandler.invocationCount());
    }

    private static Session newSessionProxy(RecordingSessionInvocationHandler handler) {
        return (Session) Proxy.newProxyInstance(
                HibernateTemplateInnerCloseSuppressingInvocationHandlerTest.class.getClassLoader(),
                new Class<?>[] {Session.class},
                handler);
    }

    private static final class TestHibernateTemplate extends HibernateTemplate {
        Session createCloseSuppressingProxy(Session session) {
            return createSessionProxy(session);
        }
    }

    private static final class RecordingSessionInvocationHandler implements InvocationHandler {
        private int invocationCount;

        private String lastInvokedMethodName;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            this.invocationCount++;
            this.lastInvokedMethodName = method.getName();
            if (method.getName().equals("isOpen")) {
                return true;
            }
            String message = "Unexpected Session method: " + method.getName();
            throw new UnsupportedOperationException(message);
        }

        int invocationCount() {
            return this.invocationCount;
        }

        String lastInvokedMethodName() {
            return this.lastInvokedMethodName;
        }
    }
}
