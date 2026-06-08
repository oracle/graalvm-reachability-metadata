/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_orm;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.orm.hibernate5.HibernateTemplate;

public class HibernateTemplateInnerCloseSuppressingInvocationHandlerTest {
    @Test
    void executeExposesCloseSuppressingSessionProxy() {
        CountingSessionHandler sessionHandler = new CountingSessionHandler();
        Session targetSession = sessionProxy(sessionHandler);
        SessionFactory sessionFactory = sessionFactoryProxy(targetSession);
        HibernateTemplate template = new HibernateTemplate(sessionFactory);

        Boolean open = template.execute(session -> {
            assertThat(session).isNotSameAs(targetSession);
            assertThat(session.isOpen()).isTrue();
            session.close();
            assertThat(sessionHandler.closeCalls.get()).isZero();
            return session.isOpen();
        });

        assertThat(open).isTrue();
        assertThat(sessionHandler.isOpenCalls.get()).isEqualTo(3);
        assertThat(sessionHandler.flushModeCalls.get()).isEqualTo(1);
        assertThat(sessionHandler.closeCalls.get()).isEqualTo(1);
    }

    private static Session sessionProxy(CountingSessionHandler sessionHandler) {
        return (Session) Proxy.newProxyInstance(
                Session.class.getClassLoader(), new Class<?>[] {Session.class}, sessionHandler);
    }

    private static SessionFactory sessionFactoryProxy(Session session) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            switch (method.getName()) {
                case "getCurrentSession":
                    throw new HibernateException("No current session bound to this test");
                case "openSession":
                    return session;
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "SessionFactory test proxy";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (SessionFactory) Proxy.newProxyInstance(
                SessionFactory.class.getClassLoader(), new Class<?>[] {SessionFactory.class}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }

    private static final class CountingSessionHandler implements InvocationHandler {
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final AtomicInteger flushModeCalls = new AtomicInteger();
        private final AtomicInteger isOpenCalls = new AtomicInteger();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "close":
                    closeCalls.incrementAndGet();
                    return null;
                case "setHibernateFlushMode":
                    assertThat(args[0]).isEqualTo(FlushMode.MANUAL);
                    flushModeCalls.incrementAndGet();
                    return null;
                case "isOpen":
                    isOpenCalls.incrementAndGet();
                    return true;
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "Session test proxy";
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}
