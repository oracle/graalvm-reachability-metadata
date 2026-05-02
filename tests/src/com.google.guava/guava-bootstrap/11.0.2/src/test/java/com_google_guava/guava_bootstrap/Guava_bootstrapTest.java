/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava_bootstrap;

import java.util.Arrays;
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

    private static void shutdownAndAwait(ExecutorService executor) {
        executor.shutdownNow();
        try {
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for executor termination", e);
        }
    }
}
