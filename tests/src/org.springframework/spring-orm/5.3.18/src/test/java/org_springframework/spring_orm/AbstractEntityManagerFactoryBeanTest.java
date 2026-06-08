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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;

public class AbstractEntityManagerFactoryBeanTest {
    @Test
    void createsProxyThatDispatchesFactoryInfoMethodsAndNativeFactoryMethods() {
        CountingEntityManagerFactoryHandler nativeFactoryHandler = new CountingEntityManagerFactoryHandler();
        EntityManagerFactory nativeFactory = entityManagerFactoryProxy(nativeFactoryHandler);
        TestEntityManagerFactoryBean factoryBean = new TestEntityManagerFactoryBean(nativeFactory);
        factoryBean.setPersistenceUnitName("orders");

        factoryBean.afterPropertiesSet();
        EntityManagerFactory exposedFactory = factoryBean.getObject();

        assertThat(exposedFactory).isNotNull();
        assertThat(Proxy.isProxyClass(exposedFactory.getClass())).isTrue();
        assertThat(exposedFactory).isInstanceOf(EntityManagerFactoryInfo.class);
        assertThat(((EntityManagerFactoryInfo) exposedFactory).getPersistenceUnitName()).isEqualTo("orders");
        assertThat(exposedFactory.isOpen()).isTrue();
        assertThat(exposedFactory.getProperties()).containsEntry("provider", "test");

        factoryBean.destroy();

        assertThat(nativeFactoryHandler.isOpenCalls.get()).isEqualTo(1);
        assertThat(nativeFactoryHandler.getPropertiesCalls.get()).isEqualTo(1);
        assertThat(nativeFactoryHandler.closeCalls.get()).isEqualTo(1);
    }

    private static EntityManagerFactory entityManagerFactoryProxy(
            CountingEntityManagerFactoryHandler nativeFactoryHandler) {
        return (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(),
                new Class<?>[] {EntityManagerFactory.class}, nativeFactoryHandler);
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

    private static final class TestEntityManagerFactoryBean extends AbstractEntityManagerFactoryBean {
        private final EntityManagerFactory nativeFactory;

        private TestEntityManagerFactoryBean(EntityManagerFactory nativeFactory) {
            this.nativeFactory = nativeFactory;
        }

        @Override
        protected EntityManagerFactory createNativeEntityManagerFactory() throws PersistenceException {
            return nativeFactory;
        }
    }

    private static final class CountingEntityManagerFactoryHandler implements InvocationHandler {
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final AtomicInteger getPropertiesCalls = new AtomicInteger();
        private final AtomicInteger isOpenCalls = new AtomicInteger();
        private final Map<String, Object> properties = Collections.singletonMap("provider", "test");

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "close":
                    closeCalls.incrementAndGet();
                    return null;
                case "getProperties":
                    getPropertiesCalls.incrementAndGet();
                    return properties;
                case "isOpen":
                    isOpenCalls.incrementAndGet();
                    return true;
                default:
                    return defaultObjectMethod(proxy, method, args, "Native EntityManagerFactory test proxy");
            }
        }
    }
}
