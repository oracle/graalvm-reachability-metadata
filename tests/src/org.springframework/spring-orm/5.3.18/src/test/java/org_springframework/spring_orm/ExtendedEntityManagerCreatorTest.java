/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_orm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerProxy;
import org.springframework.orm.jpa.ExtendedEntityManagerCreator;

public class ExtendedEntityManagerCreatorTest {
    @Test
    void createsContainerManagedEntityManagerProxyAroundFactoryCreatedEntityManager() {
        CountingEntityManagerHandler entityManagerHandler = new CountingEntityManagerHandler();
        EntityManager rawEntityManager = entityManagerProxy(entityManagerHandler);
        CountingEntityManagerFactoryHandler factoryHandler = new CountingEntityManagerFactoryHandler(rawEntityManager);
        EntityManagerFactory factory = entityManagerFactoryProxy(factoryHandler);

        EntityManager entityManager = ExtendedEntityManagerCreator.createContainerManagedEntityManager(factory);

        assertThat(entityManager).isNotSameAs(rawEntityManager);
        assertThat(entityManager).isInstanceOf(EntityManagerProxy.class);
        assertThat(((EntityManagerProxy) entityManager).getTargetEntityManager()).isSameAs(rawEntityManager);
        assertThat(entityManager.isOpen()).isTrue();
        assertThatIllegalStateException().isThrownBy(entityManager::close)
                .withMessageContaining("Cannot close a container-managed EntityManager");
        assertThat(factoryHandler.createEntityManagerCalls.get()).isEqualTo(1);
        assertThat(entityManagerHandler.getTransactionCalls.get()).isEqualTo(1);
        assertThat(entityManagerHandler.isOpenCalls.get()).isZero();
        assertThat(entityManagerHandler.closeCalls.get()).isZero();
    }

    private static EntityManagerFactory entityManagerFactoryProxy(
            CountingEntityManagerFactoryHandler factoryHandler) {
        return (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(),
                new Class<?>[] {EntityManagerFactory.class}, factoryHandler);
    }

    private static EntityManager entityManagerProxy(CountingEntityManagerHandler entityManagerHandler) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[] {EntityManager.class}, entityManagerHandler);
    }

    private static EntityTransaction entityTransactionProxy() {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> defaultObjectMethod(
                proxy, method, args, "EntityTransaction test proxy");
        return (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(), new Class<?>[] {EntityTransaction.class}, handler);
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
        private final AtomicInteger createEntityManagerCalls = new AtomicInteger();
        private final EntityManager entityManager;

        private CountingEntityManagerFactoryHandler(EntityManager entityManager) {
            this.entityManager = entityManager;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "createEntityManager":
                    createEntityManagerCalls.incrementAndGet();
                    return entityManager;
                default:
                    return defaultObjectMethod(proxy, method, args, "EntityManagerFactory test proxy");
            }
        }
    }

    private static final class CountingEntityManagerHandler implements InvocationHandler {
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final AtomicInteger getTransactionCalls = new AtomicInteger();
        private final AtomicInteger isOpenCalls = new AtomicInteger();
        private final EntityTransaction transaction = entityTransactionProxy();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "close":
                    closeCalls.incrementAndGet();
                    return null;
                case "getTransaction":
                    getTransactionCalls.incrementAndGet();
                    return transaction;
                case "isOpen":
                    isOpenCalls.incrementAndGet();
                    return true;
                default:
                    return defaultObjectMethod(proxy, method, args, "EntityManager test proxy");
            }
        }
    }
}
