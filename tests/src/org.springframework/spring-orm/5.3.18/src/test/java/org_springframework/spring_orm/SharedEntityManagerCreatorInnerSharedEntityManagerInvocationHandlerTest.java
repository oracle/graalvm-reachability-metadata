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
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.metamodel.Metamodel;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SharedEntityManagerCreatorInnerSharedEntityManagerInvocationHandlerTest {
    @Test
    void sharedEntityManagerDelegatesFactoryLevelJpaMethodsToFactory() {
        CriteriaBuilder criteriaBuilder = newInterfaceProxy(CriteriaBuilder.class, "criteriaBuilder");
        Metamodel metamodel = newInterfaceProxy(Metamodel.class, "metamodel");
        RecordingEntityManagerFactoryInvocationHandler factoryHandler =
                new RecordingEntityManagerFactoryInvocationHandler(null, criteriaBuilder, metamodel);
        EntityManagerFactory entityManagerFactory = newEntityManagerFactoryProxy(factoryHandler);

        EntityManager sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);

        assertSame(criteriaBuilder, sharedEntityManager.getCriteriaBuilder());
        assertSame(metamodel, sharedEntityManager.getMetamodel());
        assertEquals(1, factoryHandler.criteriaBuilderCalls());
        assertEquals(1, factoryHandler.metamodelCalls());
    }

    @Test
    void nonTransactionalQueryCreationInvokesTargetEntityManagerAndDefersClosingUntilQueryExecution() {
        Query rawQuery = newQueryProxy(new RecordingQueryInvocationHandler());
        RecordingEntityManagerInvocationHandler entityManagerHandler =
                new RecordingEntityManagerInvocationHandler(rawQuery);
        EntityManager targetEntityManager = newEntityManagerProxy(entityManagerHandler);
        RecordingEntityManagerFactoryInvocationHandler factoryHandler =
                new RecordingEntityManagerFactoryInvocationHandler(targetEntityManager, null, null);
        EntityManagerFactory entityManagerFactory = newEntityManagerFactoryProxy(factoryHandler);
        EntityManager sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);

        Query deferredQuery = sharedEntityManager.createQuery("select e from Example e");

        assertNotSame(rawQuery, deferredQuery);
        assertEquals(1, factoryHandler.createEntityManagerCalls());
        assertEquals(1, entityManagerHandler.createQueryCalls());
        assertEquals(0, entityManagerHandler.closeCalls());

        List<?> results = deferredQuery.getResultList();

        assertEquals(Collections.singletonList("row"), results);
        assertEquals(1, entityManagerHandler.closeCalls());
    }

    private static EntityManagerFactory newEntityManagerFactoryProxy(
            RecordingEntityManagerFactoryInvocationHandler handler) {

        return newProxy(EntityManagerFactory.class, handler);
    }

    private static EntityManager newEntityManagerProxy(RecordingEntityManagerInvocationHandler handler) {
        return newProxy(EntityManager.class, handler);
    }

    private static Query newQueryProxy(RecordingQueryInvocationHandler handler) {
        return newProxy(Query.class, handler);
    }

    private static <T> T newInterfaceProxy(Class<T> type, String description) {
        return newProxy(type, (proxy, method, args) -> {
            Object objectMethodResult = handleObjectMethod(proxy, method, args, description);
            if (objectMethodResult != null) {
                return objectMethodResult;
            }
            throw new UnsupportedOperationException("Unexpected " + description + " method: " + method.getName());
        });
    }

    private static <T> T newProxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                SharedEntityManagerCreatorInnerSharedEntityManagerInvocationHandlerTest.class.getClassLoader(),
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

        private final CriteriaBuilder criteriaBuilder;

        private final Metamodel metamodel;

        private int createEntityManagerCalls;

        private int criteriaBuilderCalls;

        private int metamodelCalls;

        RecordingEntityManagerFactoryInvocationHandler(
                EntityManager entityManager, CriteriaBuilder criteriaBuilder, Metamodel metamodel) {

            this.entityManager = entityManager;
            this.criteriaBuilder = criteriaBuilder;
            this.metamodel = metamodel;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Object objectMethodResult = handleObjectMethod(proxy, method, args, "entityManagerFactory");
            if (objectMethodResult != null) {
                return objectMethodResult;
            }
            switch (method.getName()) {
                case "createEntityManager":
                    this.createEntityManagerCalls++;
                    return this.entityManager;
                case "getCriteriaBuilder":
                    this.criteriaBuilderCalls++;
                    return this.criteriaBuilder;
                case "getMetamodel":
                    this.metamodelCalls++;
                    return this.metamodel;
                default:
                    String message = "Unexpected EntityManagerFactory method: " + method.getName();
                    throw new UnsupportedOperationException(message);
            }
        }

        int createEntityManagerCalls() {
            return this.createEntityManagerCalls;
        }

        int criteriaBuilderCalls() {
            return this.criteriaBuilderCalls;
        }

        int metamodelCalls() {
            return this.metamodelCalls;
        }
    }

    private static final class RecordingEntityManagerInvocationHandler implements InvocationHandler {
        private final Query query;

        private int createQueryCalls;

        private int closeCalls;

        RecordingEntityManagerInvocationHandler(Query query) {
            this.query = query;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Object objectMethodResult = handleObjectMethod(proxy, method, args, "entityManager");
            if (objectMethodResult != null) {
                return objectMethodResult;
            }
            switch (method.getName()) {
                case "createQuery":
                    this.createQueryCalls++;
                    return this.query;
                case "isOpen":
                    return true;
                case "close":
                    this.closeCalls++;
                    return null;
                default:
                    throw new UnsupportedOperationException("Unexpected EntityManager method: " + method.getName());
            }
        }

        int createQueryCalls() {
            return this.createQueryCalls;
        }

        int closeCalls() {
            return this.closeCalls;
        }
    }

    private static final class RecordingQueryInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Object objectMethodResult = handleObjectMethod(proxy, method, args, "query");
            if (objectMethodResult != null) {
                return objectMethodResult;
            }
            if (method.getName().equals("getResultList")) {
                return Collections.singletonList("row");
            }
            throw new UnsupportedOperationException("Unexpected Query method: " + method.getName());
        }
    }
}
