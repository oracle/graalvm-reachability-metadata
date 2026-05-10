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

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractEntityManagerFactoryBeanTest {
    @Test
    void afterPropertiesSetCreatesEntityManagerFactoryProxy() {
        RecordingEntityManagerFactoryInvocationHandler nativeHandler =
                new RecordingEntityManagerFactoryInvocationHandler(false);
        EntityManagerFactory nativeFactory = newEntityManagerFactoryProxy(nativeHandler);
        TestEntityManagerFactoryBean factoryBean = new TestEntityManagerFactoryBean(nativeFactory);

        factoryBean.afterPropertiesSet();

        EntityManagerFactory proxyFactory = factoryBean.getObject();
        assertTrue(Proxy.isProxyClass(proxyFactory.getClass()));
        assertTrue(proxyFactory instanceof EntityManagerFactoryInfo);
        assertSame(nativeFactory, factoryBean.getNativeEntityManagerFactory());
    }

    @Test
    void entityManagerFactoryInfoMethodsAreHandledByFactoryBeanProxy() {
        RecordingEntityManagerFactoryInvocationHandler nativeHandler =
                new RecordingEntityManagerFactoryInvocationHandler(true);
        EntityManagerFactory nativeFactory = newEntityManagerFactoryProxy(nativeHandler);
        TestEntityManagerFactoryBean factoryBean = new TestEntityManagerFactoryBean(nativeFactory);
        factoryBean.setPersistenceUnitName("test-unit");

        factoryBean.afterPropertiesSet();

        EntityManagerFactoryInfo proxyInfo = (EntityManagerFactoryInfo) factoryBean.getObject();
        assertEquals("test-unit", proxyInfo.getPersistenceUnitName());
        assertSame(nativeFactory, proxyInfo.getNativeEntityManagerFactory());
        assertEquals(0, nativeHandler.invocationCount());
    }

    @Test
    void entityManagerFactoryMethodsAreDelegatedToNativeFactory() {
        RecordingEntityManagerFactoryInvocationHandler nativeHandler =
                new RecordingEntityManagerFactoryInvocationHandler(true);
        EntityManagerFactory nativeFactory = newEntityManagerFactoryProxy(nativeHandler);
        TestEntityManagerFactoryBean factoryBean = new TestEntityManagerFactoryBean(nativeFactory);
        factoryBean.afterPropertiesSet();

        EntityManagerFactory proxyFactory = factoryBean.getObject();

        assertTrue(proxyFactory.isOpen());
        assertEquals(1, nativeHandler.invocationCount());
        assertEquals("isOpen", nativeHandler.lastInvokedMethodName());
    }

    private static EntityManagerFactory newEntityManagerFactoryProxy(
            RecordingEntityManagerFactoryInvocationHandler handler) {

        return (EntityManagerFactory) Proxy.newProxyInstance(
                AbstractEntityManagerFactoryBeanTest.class.getClassLoader(),
                new Class<?>[] {EntityManagerFactory.class},
                handler);
    }

    private static final class TestEntityManagerFactoryBean extends AbstractEntityManagerFactoryBean {
        private final EntityManagerFactory nativeFactory;

        TestEntityManagerFactoryBean(EntityManagerFactory nativeFactory) {
            this.nativeFactory = nativeFactory;
        }

        @Override
        protected EntityManagerFactory createNativeEntityManagerFactory() throws PersistenceException {
            return this.nativeFactory;
        }
    }

    private static final class RecordingEntityManagerFactoryInvocationHandler implements InvocationHandler {
        private final boolean open;

        private int invocationCount;

        private String lastInvokedMethodName;

        RecordingEntityManagerFactoryInvocationHandler(boolean open) {
            this.open = open;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Object objectMethodResult = handleObjectMethod(proxy, method, args);
            if (objectMethodResult != null) {
                return objectMethodResult;
            }
            this.invocationCount++;
            this.lastInvokedMethodName = method.getName();
            if (method.getName().equals("isOpen")) {
                return this.open;
            }
            String message = "Unexpected EntityManagerFactory method: " + method.getName();
            throw new UnsupportedOperationException(message);
        }

        int invocationCount() {
            return this.invocationCount;
        }

        String lastInvokedMethodName() {
            return this.lastInvokedMethodName;
        }
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() != Object.class) {
            return null;
        }
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "nativeEntityManagerFactory";
            default:
                return null;
        }
    }
}
