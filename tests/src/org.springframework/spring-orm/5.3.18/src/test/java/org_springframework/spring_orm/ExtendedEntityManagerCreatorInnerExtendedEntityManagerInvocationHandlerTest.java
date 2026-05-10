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
import org.springframework.orm.jpa.ExtendedEntityManagerCreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtendedEntityManagerCreatorInnerExtendedEntityManagerInvocationHandlerTest {
    @Test
    void containerManagedEntityManagerDelegatesJpaInterfaceMethodToTargetEntityManager() {
        Object entity = new Object();
        RecordingEntityManagerInvocationHandler entityManagerHandler =
                new RecordingEntityManagerInvocationHandler(entity);
        EntityManager rawEntityManager = newProxy(EntityManager.class, entityManagerHandler);
        EntityManagerFactory entityManagerFactory = newProxy(
                EntityManagerFactory.class,
                new RecordingEntityManagerFactoryInvocationHandler(rawEntityManager));

        EntityManager extendedEntityManager =
                ExtendedEntityManagerCreator.createContainerManagedEntityManager(entityManagerFactory, null, false);

        assertTrue(extendedEntityManager.contains(entity));
        assertEquals(1, entityManagerHandler.containsCalls());
        assertEquals(1, entityManagerHandler.getTransactionCalls());
    }

    private static <T> T newProxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                ExtendedEntityManagerCreatorInnerExtendedEntityManagerInvocationHandlerTest.class.getClassLoader(),
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
                return this.entityManager;
            }
            throw new UnsupportedOperationException("Unexpected EntityManagerFactory method: " + method.getName());
        }
    }

    private static final class RecordingEntityManagerInvocationHandler implements InvocationHandler {
        private final Object containedEntity;

        private final EntityTransaction transaction = newProxy(
                EntityTransaction.class,
                (proxy, method, args) -> handleObjectMethod(proxy, method, args, "entityTransaction"));

        private int getTransactionCalls;

        private int containsCalls;

        RecordingEntityManagerInvocationHandler(Object containedEntity) {
            this.containedEntity = containedEntity;
        }

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
                case "contains":
                    this.containsCalls++;
                    return args[0] == this.containedEntity;
                default:
                    throw new UnsupportedOperationException("Unexpected EntityManager method: " + method.getName());
            }
        }

        int getTransactionCalls() {
            return this.getTransactionCalls;
        }

        int containsCalls() {
            return this.containsCalls;
        }
    }
}
