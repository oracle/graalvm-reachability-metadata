/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava_bootstrap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Guava_bootstrapTest {

    @Test
    void submitVariantsReturnExpectedResults() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> callableResult = executor.submit(() -> "callable");
            Future<String> runnableWithResult = executor.submit(() -> { }, "runnable-result");
            Future<?> runnableResult = executor.submit(() -> { });

            assertThat(callableResult.get(1, TimeUnit.SECONDS)).isEqualTo("callable");
            assertThat(runnableWithResult.get(1, TimeUnit.SECONDS)).isEqualTo("runnable-result");
            assertThat(runnableResult.get(1, TimeUnit.SECONDS)).isNull();
        } finally {
            shutdownAndAwait(executor);
        }
    }

    @Test
    void invokeAllAndInvokeAnyCompleteBoundedTaskSets() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = Arrays.asList(() -> 1, () -> 2, () -> 3);
            List<Future<Integer>> futures = executor.invokeAll(tasks, 1, TimeUnit.SECONDS);

            assertThat(futures).hasSize(3);
            assertThat(futures).allSatisfy(future -> {
                assertThat(future.isDone()).isTrue();
                assertThat(future.isCancelled()).isFalse();
            });
            assertThat(futures.get(0).get(1, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(futures.get(1).get(1, TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(futures.get(2).get(1, TimeUnit.SECONDS)).isEqualTo(3);

            String firstResult = executor.invokeAny(List.of(() -> "alpha", () -> "beta"), 1, TimeUnit.SECONDS);
            assertThat(firstResult).isIn("alpha", "beta");
        } finally {
            shutdownAndAwait(executor);
        }
    }

    @Test
    void shutdownNowStopsRunningWorkAndReturnsQueuedWork() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch runningTaskStarted = new CountDownLatch(1);

        Future<?> runningTask = executor.submit(() -> {
            runningTaskStarted.countDown();
            TimeUnit.SECONDS.sleep(5);
            return null;
        });
        assertThat(runningTaskStarted.await(1, TimeUnit.SECONDS)).isTrue();

        Future<String> queuedTask = executor.submit(() -> "never-run");
        List<Runnable> queuedWork = executor.shutdownNow();

        assertThat(executor.isShutdown()).isTrue();
        assertThat(queuedWork).contains((Runnable) queuedTask);
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.isTerminated()).isTrue();
        assertThatThrownBy(() -> runningTask.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InterruptedException.class);
    }

    @Test
    void invokeAnyTimesOutWhenNoTaskCompletesBeforeDeadline() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        Callable<String> blockedTask = () -> {
            releaseTasks.await(5, TimeUnit.SECONDS);
            return "finished";
        };

        try {
            assertThatThrownBy(() -> executor.invokeAny(List.of(blockedTask, blockedTask), 100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseTasks.countDown();
            shutdownAndAwait(executor);
        }
    }

    @Test
    void invokeAnyPropagatesFailureWhenEveryTaskFails() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> failingTask = () -> {
                throw new IllegalStateException("controlled failure");
            };

            assertThatThrownBy(() -> executor.invokeAny(List.of(failingTask, failingTask), 1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        } finally {
            shutdownAndAwait(executor);
        }
    }

    @Test
    void closePerformsOrderlyShutdownAndWaitsUntilTerminated() {
        RecordingExecutorService executor = new RecordingExecutorService();

        executor.close();

        assertThat(executor.shutdownCalls).isEqualTo(1);
        assertThat(executor.shutdownNowCalls).isZero();
        assertThat(executor.awaitTerminationCalls).isEqualTo(2);
        assertThat(executor.lastAwaitTimeout).isEqualTo(1L);
        assertThat(executor.lastAwaitTimeUnit).isEqualTo(TimeUnit.DAYS);
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void closeForcesShutdownAndRestoresInterruptStatusAfterInterruptedWait() {
        InterruptingExecutorService executor = new InterruptingExecutorService();
        assertThat(Thread.interrupted()).isFalse();

        try {
            executor.close();

            assertThat(executor.shutdownCalls).isEqualTo(1);
            assertThat(executor.shutdownNowCalls).isEqualTo(1);
            assertThat(executor.awaitTerminationCalls).isEqualTo(2);
            assertThat(executor.lastAwaitTimeout).isEqualTo(1L);
            assertThat(executor.lastAwaitTimeUnit).isEqualTo(TimeUnit.DAYS);
            assertThat(executor.isTerminated()).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static void shutdownAndAwait(ExecutorService executor) {
        executor.shutdownNow();
        try {
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for executor termination", e);
        }
    }

    private static final class InterruptingExecutorService implements ExecutorService {
        private int shutdownCalls;
        private int shutdownNowCalls;
        private int awaitTerminationCalls;
        private long lastAwaitTimeout;
        private TimeUnit lastAwaitTimeUnit;
        private boolean shutdown;
        private boolean terminated;

        @Override
        public void shutdown() {
            shutdownCalls++;
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownNowCalls++;
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return terminated;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            awaitTerminationCalls++;
            lastAwaitTimeout = timeout;
            lastAwaitTimeUnit = unit;
            if (awaitTerminationCalls == 1) {
                throw new InterruptedException("controlled interruption");
            }
            terminated = true;
            return true;
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException("execute is not used by this test");
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException("submit is not used by this test");
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException("submit is not used by this test");
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException("submit is not used by this test");
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("invokeAll is not used by this test");
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("invokeAll is not used by this test");
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("invokeAny is not used by this test");
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("invokeAny is not used by this test");
        }
    }

    private static final class RecordingExecutorService implements ExecutorService {
        private int shutdownCalls;
        private int shutdownNowCalls;
        private int awaitTerminationCalls;
        private long lastAwaitTimeout;
        private TimeUnit lastAwaitTimeUnit;
        private boolean shutdown;
        private boolean terminated;

        @Override
        public void shutdown() {
            shutdownCalls++;
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownNowCalls++;
            shutdown = true;
            terminated = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return terminated;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            awaitTerminationCalls++;
            lastAwaitTimeout = timeout;
            lastAwaitTimeUnit = unit;
            terminated = awaitTerminationCalls >= 2;
            return terminated;
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException("execute is not used by this test");
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException("submit is not used by this test");
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException("submit is not used by this test");
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException("submit is not used by this test");
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("invokeAll is not used by this test");
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("invokeAll is not used by this test");
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("invokeAny is not used by this test");
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("invokeAny is not used by this test");
        }
    }
}
