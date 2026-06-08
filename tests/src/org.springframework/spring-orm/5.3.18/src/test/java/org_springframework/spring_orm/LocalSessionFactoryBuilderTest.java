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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.core.InfrastructureProxy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;

public class LocalSessionFactoryBuilderTest {
    @Test
    void buildSessionFactoryReturnsBackgroundBootstrapProxy() {
        CountingSessionFactoryHandler targetHandler = new CountingSessionFactoryHandler();
        SessionFactory targetSessionFactory = sessionFactoryProxy(targetHandler);
        TestLocalSessionFactoryBuilder builder = new TestLocalSessionFactoryBuilder(targetSessionFactory);
        builder.setProperty("hibernate.format_sql", "true");
        DirectAsyncTaskExecutor bootstrapExecutor = new DirectAsyncTaskExecutor();

        SessionFactory sessionFactory = builder.buildSessionFactory(bootstrapExecutor);

        assertThat(sessionFactory).isInstanceOf(SessionFactoryImplementor.class);
        assertThat(sessionFactory).isInstanceOf(InfrastructureProxy.class);
        assertThat(sessionFactory).isNotSameAs(targetSessionFactory);
        assertThat(sessionFactory.getProperties()).containsEntry("hibernate.format_sql", "true");
        assertThat(targetHandler.getPropertiesCalls.get()).isZero();
        assertThat(((InfrastructureProxy) sessionFactory).getWrappedObject()).isSameAs(targetSessionFactory);
        assertThat(sessionFactory.isOpen()).isTrue();
        assertThat(targetHandler.isOpenCalls.get()).isEqualTo(1);
        assertThat(bootstrapExecutor.callableSubmitCalls.get()).isEqualTo(1);
        assertThat(builder.buildSessionFactoryCalls.get()).isEqualTo(1);
    }

    private static SessionFactory sessionFactoryProxy(CountingSessionFactoryHandler targetHandler) {
        return (SessionFactory) Proxy.newProxyInstance(
                SessionFactoryImplementor.class.getClassLoader(),
                new Class<?>[] {SessionFactoryImplementor.class}, targetHandler);
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

    private static final class TestLocalSessionFactoryBuilder extends LocalSessionFactoryBuilder {
        private final AtomicInteger buildSessionFactoryCalls = new AtomicInteger();
        private final SessionFactory sessionFactory;

        private TestLocalSessionFactoryBuilder(SessionFactory sessionFactory) {
            super(null);
            this.sessionFactory = sessionFactory;
        }

        @Override
        public SessionFactory buildSessionFactory() {
            buildSessionFactoryCalls.incrementAndGet();
            return sessionFactory;
        }
    }

    private static final class DirectAsyncTaskExecutor implements AsyncTaskExecutor {
        private final AtomicInteger callableSubmitCalls = new AtomicInteger();

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public void execute(Runnable task, long startTimeout) {
            task.run();
        }

        @Override
        public Future<?> submit(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            callableSubmitCalls.incrementAndGet();
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                future.complete(task.call());
            }
            catch (Exception ex) {
                future.completeExceptionally(ex);
            }
            return future;
        }
    }

    private static final class CountingSessionFactoryHandler implements InvocationHandler {
        private final AtomicInteger getPropertiesCalls = new AtomicInteger();
        private final AtomicInteger isOpenCalls = new AtomicInteger();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getProperties":
                    getPropertiesCalls.incrementAndGet();
                    return Map.of();
                case "isOpen":
                    isOpenCalls.incrementAndGet();
                    return true;
                default:
                    return defaultObjectMethod(proxy, method, args, "SessionFactory test proxy");
            }
        }
    }
}
