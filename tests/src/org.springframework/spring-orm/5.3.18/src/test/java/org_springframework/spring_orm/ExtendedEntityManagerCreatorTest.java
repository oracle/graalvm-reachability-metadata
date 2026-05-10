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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerProxy;
import org.springframework.orm.jpa.ExtendedEntityManagerCreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtendedEntityManagerCreatorTest {
    @Test
    void createContainerManagedEntityManagerCreatesExtendedProxy() {
        RecordingEntityManagerInvocationHandler entityManagerHandler = new RecordingEntityManagerInvocationHandler();
        EntityManager rawEntityManager = newProxy(EntityManager.class, entityManagerHandler);
        RecordingEntityManagerFactoryInvocationHandler factoryHandler =
                new RecordingEntityManagerFactoryInvocationHandler(rawEntityManager);
        EntityManagerFactory entityManagerFactory = newProxy(EntityManagerFactory.class, factoryHandler);

        EntityManager extendedEntityManager =
                ExtendedEntityManagerCreator.createContainerManagedEntityManager(entityManagerFactory);

        assertTrue(Proxy.isProxyClass(extendedEntityManager.getClass()));
        assertTrue(extendedEntityManager instanceof EntityManagerProxy);
        assertSame(rawEntityManager, ((EntityManagerProxy) extendedEntityManager).getTargetEntityManager());
        assertTrue(extendedEntityManager.isOpen());
        assertEquals(1, factoryHandler.createEntityManagerCalls());
        assertEquals(1, entityManagerHandler.getTransactionCalls());
        assertEquals(0, entityManagerHandler.isOpenCalls());
    }

    private static <T> T newProxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                ExtendedEntityManagerCreatorTest.class.getClassLoader(),
                new Class<?>[] {type},
                handler));
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args, String description) {
        if (method.getDeclaringClass() != Object.class) {
            return null;
        }
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return description;
            default:
                return null;
        }
    }

    private static final class RecordingEntityManagerFactoryInvocationHandler implements InvocationHandler {
        private final EntityManager entityManager;

        private int createEntityManagerCalls;

        RecordingEntityManagerFactoryInvocationHandler(EntityManager entityManager) {
            this.entityManager = entityManager;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Object objectMethodResult = handleObjectMethod(proxy, method, args, "entityManagerFactory");
            if (objectMethodResult != null) {
                return objectMethodResult;
            }
            if (method.getName().equals("createEntityManager")) {
                this.createEntityManagerCalls++;
                return this.entityManager;
            }
            throw new UnsupportedOperationException("Unexpected EntityManagerFactory method: " + method.getName());
        }

        int createEntityManagerCalls() {
            return this.createEntityManagerCalls;
        }
    }

    private static final class RecordingEntityManagerInvocationHandler implements InvocationHandler {
        private final EntityTransaction transaction = newProxy(
                EntityTransaction.class,
                (proxy, method, args) -> handleObjectMethod(proxy, method, args, "entityTransaction"));

        private int getTransactionCalls;

        private int isOpenCalls;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Object objectMethodResult = handleObjectMethod(proxy, method, args, "rawEntityManager");
            if (objectMethodResult != null) {
                return objectMethodResult;
            }
            switch (method.getName()) {
                case "getTransaction":
                    this.getTransactionCalls++;
                    return this.transaction;
                case "isOpen":
                    this.isOpenCalls++;
                    return false;
                default:
                    throw new UnsupportedOperationException("Unexpected EntityManager method: " + method.getName());
            }
        }

        int getTransactionCalls() {
            return this.getTransactionCalls;
        }

        int isOpenCalls() {
            return this.isOpenCalls;
        }
    }
}
