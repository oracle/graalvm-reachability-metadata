/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava_bootstrap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Guava_bootstrapTest {
    private static final String POM_PROPERTIES_RESOURCE =
            "META-INF/maven/com.google.guava/guava-bootstrap/pom.properties";
    private static final String POM_XML_RESOURCE = "META-INF/maven/com.google.guava/guava-bootstrap/pom.xml";

    @Test
    void publishesMavenMetadataForTheBootstrapArtifact() throws IOException {
        Properties pomProperties = loadPomProperties();
        String pomXml = loadUtf8Resource(POM_XML_RESOURCE);

        assertThat(pomProperties)
                .containsEntry("groupId", "com.google.guava")
                .containsEntry("artifactId", "guava-bootstrap")
                .containsEntry("version", "11.0.2");
        assertThat(pomXml)
                .contains("<artifactId>guava-bootstrap</artifactId>")
                .contains("<name>Guava Compilation Bootstrap Classes</name>")
                .contains("<source>1.5</source>")
                .contains("<target>1.5</target>");
    }

    @Test
    void pomDocumentsWhyTheBootstrapExecutorServiceExists() throws IOException {
        String pomXml = loadUtf8Resource(POM_XML_RESOURCE);

        assertThat(pomXml)
                .contains("ExecutorService's type parameters changed between JDK5 and JDK6")
                .contains("impossible for our invokeAll/invokeAny methods")
                .contains("JDK6-like copy of")
                .contains("ExecutorService")
                .contains("used in the bootstrap")
                .contains("class path of Guava proper");
    }

    @Test
    void submitMethodsReturnCompletedFuturesForCallableAndRunnableWork() throws Exception {
        ExecutorService executorService = new ImmediateExecutorService();
        AtomicInteger sideEffect = new AtomicInteger();

        Future<String> callableResult = executorService.submit(() -> "callable-result");
        Future<Integer> runnableResult = executorService.submit(() -> sideEffect.incrementAndGet(), 42);
        Future<?> nullResult = executorService.submit(() -> {
            sideEffect.addAndGet(10);
        });

        assertThat(callableResult.get()).isEqualTo("callable-result");
        assertThat(runnableResult.get()).isEqualTo(42);
        assertThat(nullResult.get()).isNull();
        assertThat(sideEffect).hasValue(11);
        assertThat(executorService.isShutdown()).isFalse();
    }

    @Test
    void invokeAllAcceptsJdk6StyleGenericCallableCollections() throws Exception {
        ExecutorService executorService = new ImmediateExecutorService();
        List<Callable<Integer>> tasks = Arrays.asList(() -> 3, () -> 5, () -> 8);

        List<Future<Integer>> futures = executorService.invokeAll(tasks);

        assertThat(futures).hasSize(3);
        assertThat(futures.get(0).get()).isEqualTo(3);
        assertThat(futures.get(1).get()).isEqualTo(5);
        assertThat(futures.get(2).get()).isEqualTo(8);
    }

    @Test
    void timedInvokeAllUsesTheSameGenericFutureContract() throws Exception {
        ExecutorService executorService = new ImmediateExecutorService();
        List<Callable<String>> tasks = Arrays.asList(() -> "alpha", () -> "beta");

        List<Future<String>> futures = executorService.invokeAll(tasks, 1, TimeUnit.SECONDS);

        assertThat(futures.get(0).get()).isEqualTo("alpha");
        assertThat(futures.get(1).get()).isEqualTo("beta");
        assertThat(futures).allSatisfy(future -> assertThat(future.isDone()).isTrue());
    }

    @Test
    void invokeAllAcceptsCollectionsDeclaredWithCallableSubtypeWildcard() throws Exception {
        ExecutorService executorService = new ImmediateExecutorService();
        List<NumberCallable> tasks = Arrays.asList(new NumberCallable(21), new NumberCallable(34));

        List<Future<Number>> futures = invokeAllNumberTasks(executorService, tasks);

        assertThat(futures).hasSize(2);
        assertThat(futures.get(0).get()).isEqualTo(21);
        assertThat(futures.get(1).get()).isEqualTo(34);
    }

    @Test
    void invokeAnySkipsFailedTasksAndReturnsTheFirstSuccessfulValue() throws Exception {
        ExecutorService executorService = new ImmediateExecutorService();
        AtomicInteger attempts = new AtomicInteger();
        List<Callable<Integer>> tasks = Arrays.asList(
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("first task failed");
                },
                () -> {
                    attempts.incrementAndGet();
                    return 99;
                },
                () -> {
                    attempts.incrementAndGet();
                    return 100;
                });

        Integer result = executorService.invokeAny(tasks);

        assertThat(result).isEqualTo(99);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void invokeAnyReportsExecutionExceptionWhenEveryTaskFails() {
        ExecutorService executorService = new ImmediateExecutorService();
        List<Callable<String>> tasks = Arrays.asList(
                () -> {
                    throw new IllegalArgumentException("first");
                },
                () -> {
                    throw new IllegalStateException("second");
                });

        assertThatThrownBy(() -> executorService.invokeAny(tasks))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("second");
    }

    @Test
    void timedInvokeAnyHonorsAnExpiredTimeoutBeforeStartingWork() {
        ExecutorService executorService = new ImmediateExecutorService();
        AtomicInteger invocations = new AtomicInteger();
        List<Callable<Integer>> tasks = Collections.singletonList(() -> invocations.incrementAndGet());

        assertThatThrownBy(() -> executorService.invokeAny(tasks, 0, TimeUnit.NANOSECONDS))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("timeout elapsed");
        assertThat(invocations).hasValue(0);
    }

    @Test
    void shutdownRejectsNewCommandsAfterPreviouslyExecutedWorkCompletes() {
        ImmediateExecutorService executorService = new ImmediateExecutorService();
        AtomicInteger executions = new AtomicInteger();

        executorService.execute(executions::incrementAndGet);
        executorService.shutdown();

        assertThat(executions).hasValue(1);
        assertThat(executorService.isShutdown()).isTrue();
        assertThat(executorService.isTerminated()).isTrue();
        assertThatThrownBy(() -> executorService.execute(executions::incrementAndGet))
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessageContaining("shut down");
        assertThat(executions).hasValue(1);
    }

    @Test
    void shutdownNowReturnsWorkThatHasNotStartedYet() {
        QueueingExecutorService executorService = new QueueingExecutorService();
        Future<?> first = executorService.submit(() -> { });
        Future<String> second = executorService.submit(() -> "queued");

        List<Runnable> queuedWork = executorService.shutdownNow();

        assertThat(queuedWork).hasSize(2);
        assertThat(queuedWork).containsExactly((Runnable) first, (Runnable) second);
        assertThat(first.isDone()).isFalse();
        assertThat(second.isDone()).isFalse();
        assertThat(executorService.isShutdown()).isTrue();
        assertThat(executorService.isTerminated()).isTrue();
        assertThatThrownBy(() -> executorService.submit(() -> "rejected"))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void awaitTerminationReflectsWhetherShutdownHasCompleted() throws Exception {
        QueueingExecutorService executorService = new QueueingExecutorService();
        executorService.submit(() -> "queued");

        assertThat(executorService.awaitTermination(1, TimeUnit.MILLISECONDS)).isFalse();

        executorService.shutdownNow();

        assertThat(executorService.awaitTermination(1, TimeUnit.MILLISECONDS)).isTrue();
    }

    private static Properties loadPomProperties() throws IOException {
        Properties properties = new Properties();

        try (InputStream inputStream = classLoader().getResourceAsStream(POM_PROPERTIES_RESOURCE)) {
            assertThat(inputStream).isNotNull();
            properties.load(inputStream);
        }

        return properties;
    }

    private static String loadUtf8Resource(String resourceName) throws IOException {
        try (InputStream inputStream = classLoader().getResourceAsStream(resourceName)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ClassLoader classLoader() {
        return Guava_bootstrapTest.class.getClassLoader();
    }

    private static List<Future<Number>> invokeAllNumberTasks(
            ExecutorService executorService, Collection<? extends Callable<Number>> tasks) throws InterruptedException {
        return executorService.invokeAll(tasks);
    }

    private static final class NumberCallable implements Callable<Number> {
        private final Number value;

        private NumberCallable(Number value) {
            this.value = value;
        }

        @Override
        public Number call() {
            return value;
        }
    }

    private static final class ImmediateExecutorService extends AbstractTestExecutorService {
        @Override
        public void execute(Runnable command) {
            ensureRunning();
            command.run();
        }
    }

    private static final class QueueingExecutorService extends AbstractTestExecutorService {
        private final Queue<Runnable> queuedCommands = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            ensureRunning();
            queuedCommands.add(command);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> queuedWork = List.copyOf(queuedCommands);
            queuedCommands.clear();
            return queuedWork;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && queuedCommands.isEmpty();
        }
    }

    private abstract static class AbstractTestExecutorService implements ExecutorService {
        protected boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            ensureRunning();
            RunnableFuture<T> future = new FutureTask<>(task);
            execute(future);
            return future;
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            ensureRunning();
            RunnableFuture<T> future = new FutureTask<>(task, result);
            execute(future);
            return future;
        }

        @Override
        public Future<?> submit(Runnable task) {
            ensureRunning();
            RunnableFuture<?> future = new FutureTask<>(task, null);
            execute(future);
            return future;
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            ensureRunning();
            return tasks.stream().map(this::submit).toList();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            ensureRunning();
            return invokeAll(tasks);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException {
            ensureRunning();
            if (tasks.isEmpty()) {
                throw new IllegalArgumentException("tasks must not be empty");
            }
            ExecutionException lastFailure = null;
            for (Callable<T> task : tasks) {
                try {
                    return task.call();
                } catch (Exception e) {
                    lastFailure = new ExecutionException(e);
                }
            }
            throw lastFailure;
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws ExecutionException, TimeoutException {
            ensureRunning();
            if (unit.toNanos(timeout) <= 0) {
                throw new TimeoutException("timeout elapsed before any task started");
            }
            return invokeAny(tasks);
        }

        protected void ensureRunning() {
            if (shutdown) {
                throw new RejectedExecutionException("executor service has been shut down");
            }
        }
    }
}
