/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_orm;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.core.InfrastructureProxy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalSessionFactoryBuilderTest {
    @Test
    void buildSessionFactoryWithAsyncExecutorReturnsBootstrapProxy() {
        CapturingAsyncTaskExecutor executor = new CapturingAsyncTaskExecutor();
        LocalSessionFactoryBuilder builder = new LocalSessionFactoryBuilder(
                null, LocalSessionFactoryBuilderTest.class.getClassLoader());

        SessionFactory sessionFactory = builder.buildSessionFactory(executor);

        assertTrue(sessionFactory instanceof SessionFactoryImplementor);
        assertTrue(sessionFactory instanceof InfrastructureProxy);
        assertEquals(1, executor.submittedCallableCount());
        assertFalse(executor.submittedFuture().isDone());
        assertSame(builder.getProperties(), sessionFactory.getProperties());
        assertEquals(System.identityHashCode(sessionFactory), sessionFactory.hashCode());
        assertTrue(sessionFactory.equals(sessionFactory));
    }

    private static final class CapturingAsyncTaskExecutor implements AsyncTaskExecutor {
        private final CompletableFuture<SessionFactory> submittedFuture = new CompletableFuture<>();

        private int submittedCallableCount;

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
            return (Future<T>) this.submittedFuture;
        }

        int submittedCallableCount() {
            return this.submittedCallableCount;
        }

        Future<SessionFactory> submittedFuture() {
            return this.submittedFuture;
        }
    }
}
