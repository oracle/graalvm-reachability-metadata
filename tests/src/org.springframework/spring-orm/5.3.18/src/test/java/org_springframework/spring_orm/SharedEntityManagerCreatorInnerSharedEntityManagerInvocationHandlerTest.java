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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.metamodel.Metamodel;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

public class SharedEntityManagerCreatorInnerSharedEntityManagerInvocationHandlerTest {
    @Test
    void delegatesFactoryLevelJpaMethodsWithoutOpeningEntityManager() {
        CountingEntityManagerFactoryHandler factoryHandler = new CountingEntityManagerFactoryHandler();
        EntityManagerFactory factory = entityManagerFactoryProxy(factoryHandler);
        EntityManager sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(factory);

        CriteriaBuilder criteriaBuilder = sharedEntityManager.getCriteriaBuilder();
        Metamodel metamodel = sharedEntityManager.getMetamodel();

        assertThat(criteriaBuilder).isSameAs(factoryHandler.criteriaBuilder);
        assertThat(metamodel).isSameAs(factoryHandler.metamodel);
        assertThat(factoryHandler.criteriaBuilderCalls.get()).isEqualTo(1);
        assertThat(factoryHandler.metamodelCalls.get()).isEqualTo(1);
        assertThat(factoryHandler.createEntityManagerCalls.get()).isZero();
    }

    @Test
    void defersQueryExecutionAndClosesTemporaryEntityManagerAfterTerminalOperation() {
        CountingEntityManagerHandler entityManagerHandler = new CountingEntityManagerHandler();
        CountingEntityManagerFactoryHandler factoryHandler = new CountingEntityManagerFactoryHandler(
                entityManagerProxy(entityManagerHandler));
        EntityManagerFactory factory = entityManagerFactoryProxy(factoryHandler);
        EntityManager sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(factory);

        Query query = sharedEntityManager.createQuery("select name from Book name");
        Query fluentQuery = query.setMaxResults(1);
        List<?> result = fluentQuery.getResultList();

        assertThat(fluentQuery).isSameAs(query);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("Spring ORM");
        assertThat(factoryHandler.createEntityManagerCalls.get()).isEqualTo(1);
        assertThat(entityManagerHandler.createQueryCalls.get()).isEqualTo(1);
        assertThat(entityManagerHandler.closeCalls.get()).isEqualTo(1);
        assertThat(entityManagerHandler.queryHandler.setMaxResultsCalls.get()).isEqualTo(1);
        assertThat(entityManagerHandler.queryHandler.getResultListCalls.get()).isEqualTo(1);
    }

    private static EntityManagerFactory entityManagerFactoryProxy(CountingEntityManagerFactoryHandler factoryHandler) {
        return (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(),
                new Class<?>[] {EntityManagerFactory.class}, factoryHandler);
    }

    private static EntityManager entityManagerProxy(CountingEntityManagerHandler entityManagerHandler) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[] {EntityManager.class}, entityManagerHandler);
    }

    private static Query queryProxy(CountingQueryHandler queryHandler) {
        return (Query) Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[] {Query.class}, queryHandler);
    }

    private static CriteriaBuilder criteriaBuilderProxy() {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> defaultObjectMethod(
                proxy, method, args, "CriteriaBuilder test proxy");
        return (CriteriaBuilder) Proxy.newProxyInstance(
                CriteriaBuilder.class.getClassLoader(), new Class<?>[] {CriteriaBuilder.class}, handler);
    }

    private static Metamodel metamodelProxy() {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> defaultObjectMethod(
                proxy, method, args, "Metamodel test proxy");
        return (Metamodel) Proxy.newProxyInstance(
                Metamodel.class.getClassLoader(), new Class<?>[] {Metamodel.class}, handler);
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
        private final AtomicInteger criteriaBuilderCalls = new AtomicInteger();
        private final AtomicInteger metamodelCalls = new AtomicInteger();
        private final CriteriaBuilder criteriaBuilder = criteriaBuilderProxy();
        private final Metamodel metamodel = metamodelProxy();
        private final EntityManager entityManager;

        private CountingEntityManagerFactoryHandler() {
            this(null);
        }

        private CountingEntityManagerFactoryHandler(EntityManager entityManager) {
            this.entityManager = entityManager;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "createEntityManager":
                    createEntityManagerCalls.incrementAndGet();
                    return entityManager;
                case "getCriteriaBuilder":
                    criteriaBuilderCalls.incrementAndGet();
                    return criteriaBuilder;
                case "getMetamodel":
                    metamodelCalls.incrementAndGet();
                    return metamodel;
                default:
                    return defaultObjectMethod(proxy, method, args, "EntityManagerFactory test proxy");
            }
        }
    }

    private static final class CountingEntityManagerHandler implements InvocationHandler {
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final AtomicInteger createQueryCalls = new AtomicInteger();
        private final CountingQueryHandler queryHandler = new CountingQueryHandler();
        private final Query query = queryProxy(queryHandler);

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "createQuery":
                    createQueryCalls.incrementAndGet();
                    assertThat(args[0]).isEqualTo("select name from Book name");
                    return query;
                case "close":
                    closeCalls.incrementAndGet();
                    return null;
                case "isOpen":
                    return true;
                default:
                    return defaultObjectMethod(proxy, method, args, "EntityManager test proxy");
            }
        }
    }

    private static final class CountingQueryHandler implements InvocationHandler {
        private final AtomicInteger getResultListCalls = new AtomicInteger();
        private final AtomicInteger setMaxResultsCalls = new AtomicInteger();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setMaxResults":
                    setMaxResultsCalls.incrementAndGet();
                    assertThat(args[0]).isEqualTo(1);
                    return proxy;
                case "getResultList":
                    getResultListCalls.incrementAndGet();
                    return Collections.singletonList("Spring ORM");
                default:
                    return defaultObjectMethod(proxy, method, args, "Query test proxy");
            }
        }
    }
}
