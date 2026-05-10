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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalSessionFactoryBuilderInnerBootstrapSessionFactoryInvocationHandlerTest {
    @Test
    void delegatesRegularSessionFactoryMethodsToCompletedBootstrapTarget() {
        RecordingSessionFactoryInvocationHandler targetHandler = new RecordingSessionFactoryInvocationHandler();
        SessionFactory targetSessionFactory = newSessionFactoryProxy(targetHandler);
        CompletedAsyncTaskExecutor executor = new CompletedAsyncTaskExecutor(targetSessionFactory);
        ClassLoader classLoader = LocalSessionFactoryBuilderInnerBootstrapSessionFactoryInvocationHandlerTest.class
                .getClassLoader();
        LocalSessionFactoryBuilder builder = new LocalSessionFactoryBuilder(null, classLoader);

        SessionFactory bootstrapProxy = builder.buildSessionFactory(executor);

        assertTrue(bootstrapProxy.isOpen());
        assertEquals(1, executor.submittedCallableCount());
        assertEquals(1, targetHandler.invocationCount());
        assertEquals("isOpen", targetHandler.lastInvokedMethodName());
    }

    private static SessionFactory newSessionFactoryProxy(RecordingSessionFactoryInvocationHandler handler) {
        return (SessionFactory) Proxy.newProxyInstance(
                LocalSessionFactoryBuilderInnerBootstrapSessionFactoryInvocationHandlerTest.class.getClassLoader(),
                new Class<?>[] {SessionFactoryImplementor.class},
                handler);
    }

    private static final class CompletedAsyncTaskExecutor implements AsyncTaskExecutor {
        private final SessionFactory sessionFactory;

        private int submittedCallableCount;

        CompletedAsyncTaskExecutor(SessionFactory sessionFactory) {
            this.sessionFactory = sessionFactory;
        }

        @Override
        public void execute(Runnable task) {
            throw new UnsupportedOperationException("Runnable execution is not used by this test");
        }

        @Override
        public void execute(Runnable task, long startTimeout) {
            throw new UnsupportedOperationException("Timed Runnable execution is not used by this test");
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException("Runnable submission is not used by this test");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Future<T> submit(Callable<T> task) {
            this.submittedCallableCount++;
            return (Future<T>) CompletableFuture.completedFuture(this.sessionFactory);
        }

        int submittedCallableCount() {
            return this.submittedCallableCount;
        }
    }

    private static final class RecordingSessionFactoryInvocationHandler implements InvocationHandler {
        private int invocationCount;

        private String lastInvokedMethodName;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            this.invocationCount++;
            this.lastInvokedMethodName = method.getName();
            if (method.getName().equals("isOpen")) {
                return true;
            }
            String message = "Unexpected SessionFactory method: " + method.getName();
            throw new UnsupportedOperationException(message);
        }

        int invocationCount() {
            return this.invocationCount;
        }

        String lastInvokedMethodName() {
            return this.lastInvokedMethodName;
        }
    }
}
