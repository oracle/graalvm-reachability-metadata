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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.vendor.HibernateJpaSessionFactoryBean;

public class HibernateJpaSessionFactoryBeanTest {
    @Test
    void exposesSessionFactoryFromHibernateEntityManagerFactory() {
        CountingSessionFactoryHandler sessionFactoryHandler = new CountingSessionFactoryHandler();
        SessionFactory sessionFactory = sessionFactoryProxy(sessionFactoryHandler);
        CountingEntityManagerFactoryHandler entityManagerFactoryHandler =
                new CountingEntityManagerFactoryHandler(sessionFactory);
        HibernateEntityManagerFactory entityManagerFactory = entityManagerFactoryProxy(entityManagerFactoryHandler);
        HibernateJpaSessionFactoryBean factoryBean = new HibernateJpaSessionFactoryBean();
        factoryBean.setEntityManagerFactory(entityManagerFactory);

        SessionFactory exposedSessionFactory = factoryBean.getObject();

        assertThat(exposedSessionFactory).isSameAs(sessionFactory);
        assertThat(factoryBean.getObjectType()).isEqualTo(SessionFactory.class);
        assertThat(factoryBean.isSingleton()).isTrue();
        assertThat(exposedSessionFactory.isOpen()).isTrue();
        assertThat(entityManagerFactoryHandler.getSessionFactoryCalls.get()).isEqualTo(1);
        assertThat(sessionFactoryHandler.isOpenCalls.get()).isEqualTo(1);
    }

    private static HibernateEntityManagerFactory entityManagerFactoryProxy(
            CountingEntityManagerFactoryHandler entityManagerFactoryHandler) {
        return (HibernateEntityManagerFactory) Proxy.newProxyInstance(
                HibernateEntityManagerFactory.class.getClassLoader(),
                new Class<?>[] {HibernateEntityManagerFactory.class}, entityManagerFactoryHandler);
    }

    private static SessionFactory sessionFactoryProxy(CountingSessionFactoryHandler sessionFactoryHandler) {
        return (SessionFactory) Proxy.newProxyInstance(
                SessionFactoryImplementor.class.getClassLoader(),
                new Class<?>[] {SessionFactoryImplementor.class}, sessionFactoryHandler);
    }

    private static Object defaultObjectMethod(Object proxy, Method method, Object[] args, String description) {
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return description;
            default:
                return defaultValue(method.getReturnType());
        }
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

    private static final class CountingEntityManagerFactoryHandler implements InvocationHandler {
        private final AtomicInteger getSessionFactoryCalls = new AtomicInteger();
        private final SessionFactory sessionFactory;

        private CountingEntityManagerFactoryHandler(SessionFactory sessionFactory) {
            this.sessionFactory = sessionFactory;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("getSessionFactory")) {
                getSessionFactoryCalls.incrementAndGet();
                return sessionFactory;
            }
            return defaultObjectMethod(proxy, method, args, "Hibernate EntityManagerFactory test proxy");
        }
    }

    private static final class CountingSessionFactoryHandler implements InvocationHandler {
        private final AtomicInteger isOpenCalls = new AtomicInteger();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getProperties":
                    return Map.of();
                case "isOpen":
                    isOpenCalls.incrementAndGet();
                    return true;
                default:
                    return defaultObjectMethod(proxy, method, args, "Hibernate SessionFactory test proxy");
            }
        }
    }
}
