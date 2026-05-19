/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.context_propagation;

import io.micrometer.context.ContextAccessor;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextScheduledExecutorService;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.ThreadLocalAccessor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Context_propagationTest {
    private static final String FIRST_KEY = "first";
    private static final String SECOND_KEY = "second";

    @Test
    void capturesThreadLocalValuesAndRestoresPreviousValuesAfterScope() {
        ThreadLocal<String> firstThreadLocal = new ThreadLocal<>();
        ThreadLocal<String> secondThreadLocal = new ThreadLocal<>();
        ContextRegistry registry = new ContextRegistry()
                .registerThreadLocalAccessor(FIRST_KEY, firstThreadLocal)
                .registerThreadLocalAccessor(SECOND_KEY, secondThreadLocal);
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                .clearMissing(true)
                .captureKeyPredicate(FIRST_KEY::equals)
                .build();

        firstThreadLocal.set("captured-first");
        secondThreadLocal.set("not-captured");
        ContextSnapshot snapshot = factory.captureAll();

        firstThreadLocal.set("previous-first");
        secondThreadLocal.set("previous-second");
        try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
            assertThat(firstThreadLocal.get()).isEqualTo("captured-first");
            assertThat(secondThreadLocal.get()).isNull();
        }

        assertThat(firstThreadLocal.get()).isEqualTo("previous-first");
        assertThat(secondThreadLocal.get()).isEqualTo("previous-second");
    }

    @Test
    void capturesContextValuesAndUpdatesAnotherContextWithKeyFiltering() {
        ContextRegistry registry = new ContextRegistry().registerContextAccessor(new TestContextAccessor());
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                .build();
        TestContext sourceContext = new TestContext()
                .put(FIRST_KEY, "source-first")
                .put(SECOND_KEY, "source-second");
        TestContext targetContext = new TestContext().put(SECOND_KEY, "existing-second");

        ContextSnapshot snapshot = factory.captureFrom(sourceContext);
        TestContext updatedContext = snapshot.updateContext(targetContext, FIRST_KEY::equals);

        assertThat(updatedContext).isSameAs(targetContext);
        assertThat(targetContext.get(FIRST_KEY)).isEqualTo("source-first");
        assertThat(targetContext.get(SECOND_KEY)).isEqualTo("existing-second");
        assertThat(snapshot.updateContext(new TestContext()).asMap())
                .containsEntry(FIRST_KEY, "source-first")
                .containsEntry(SECOND_KEY, "source-second");
    }

    @Test
    void capturesThreadLocalsAndContextObjectsWithLaterSourcesTakingPrecedence() {
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        ContextRegistry registry = new ContextRegistry()
                .registerThreadLocalAccessor(FIRST_KEY, threadLocal)
                .registerContextAccessor(new TestContextAccessor());
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                .build();

        threadLocal.set("from-thread-local");
        ContextSnapshot snapshot = factory.captureAll(new TestContext().put(FIRST_KEY, "from-context"));
        threadLocal.set("current");

        try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
            assertThat(threadLocal.get()).isEqualTo("from-context");
        }
        assertThat(threadLocal.get()).isEqualTo("current");
    }

    @Test
    void setsThreadLocalsDirectlyFromContextAndRestoresThemWhenClosed() {
        ThreadLocal<String> firstThreadLocal = new ThreadLocal<>();
        ThreadLocal<String> secondThreadLocal = new ThreadLocal<>();
        ContextRegistry registry = new ContextRegistry()
                .registerThreadLocalAccessor(FIRST_KEY, firstThreadLocal)
                .registerThreadLocalAccessor(SECOND_KEY, secondThreadLocal)
                .registerContextAccessor(new TestContextAccessor());
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                .clearMissing(true)
                .build();
        TestContext context = new TestContext().put(FIRST_KEY, "from-context");

        firstThreadLocal.set("previous-first");
        secondThreadLocal.set("previous-second");
        try (ContextSnapshot.Scope scope = factory.setThreadLocalsFrom(context)) {
            assertThat(firstThreadLocal.get()).isEqualTo("from-context");
            assertThat(secondThreadLocal.get()).isNull();
        }

        assertThat(firstThreadLocal.get()).isEqualTo("previous-first");
        assertThat(secondThreadLocal.get()).isEqualTo("previous-second");
    }

    @Test
    void wrapperMethodsSetCapturedThreadLocalsAroundUserCallbacks() throws Exception {
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        ContextRegistry registry = new ContextRegistry().registerThreadLocalAccessor(FIRST_KEY, threadLocal);
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                .build();

        threadLocal.set("captured");
        ContextSnapshot snapshot = factory.captureAll();
        threadLocal.set("outside");

        AtomicReference<String> runnableValue = new AtomicReference<>();
        snapshot.wrap((Runnable) () -> runnableValue.set(threadLocal.get())).run();
        assertThat(runnableValue).hasValue("captured");
        assertThat(threadLocal.get()).isEqualTo("outside");

        Callable<String> callable = snapshot.wrap(() -> threadLocal.get() + "-callable");
        assertThat(callable.call()).isEqualTo("captured-callable");
        assertThat(threadLocal.get()).isEqualTo("outside");

        AtomicReference<String> consumerValue = new AtomicReference<>();
        snapshot.wrap((String suffix) -> consumerValue.set(threadLocal.get() + suffix)).accept("-consumer");
        assertThat(consumerValue).hasValue("captured-consumer");
        assertThat(threadLocal.get()).isEqualTo("outside");

        AtomicReference<String> executorValue = new AtomicReference<>();
        Executor directExecutor = Runnable::run;
        snapshot.wrapExecutor(directExecutor).execute(() -> executorValue.set(threadLocal.get()));
        assertThat(executorValue).hasValue("captured");
        assertThat(threadLocal.get()).isEqualTo("outside");
    }

    @Test
    void executorServiceWrapperCapturesContextAtTaskSubmission() throws Exception {
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        ContextRegistry registry = new ContextRegistry().registerThreadLocalAccessor(FIRST_KEY, threadLocal);
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                .build();
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ExecutorService executor = ContextExecutorService.wrap(delegate, () -> factory.captureAll());
        try {
            threadLocal.set("submitted");
            Future<String> submitted = executor.submit(threadLocal::get);
            threadLocal.set("changed-after-submit");

            assertThat(submitted.get(5, TimeUnit.SECONDS)).isEqualTo("submitted");

            threadLocal.set("invoke-all");
            List<Callable<String>> tasks = Arrays.asList(threadLocal::get, threadLocal::get);
            List<Future<String>> futures = executor.invokeAll(tasks, 5, TimeUnit.SECONDS);
            assertThat(futures).hasSize(2);
            assertThat(futures.get(0).get(5, TimeUnit.SECONDS)).isEqualTo("invoke-all");
            assertThat(futures.get(1).get(5, TimeUnit.SECONDS)).isEqualTo("invoke-all");
        }
        finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void scheduledExecutorServiceWrapperPropagatesContextToDelayedTasks() throws Exception {
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        ContextRegistry registry = new ContextRegistry().registerThreadLocalAccessor(FIRST_KEY, threadLocal);
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                .build();
        ScheduledExecutorService delegate = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService executor = ContextScheduledExecutorService.wrap(delegate, () -> factory.captureAll());
        try {
            threadLocal.set("scheduled");
            ScheduledFuture<String> scheduled = executor.schedule(threadLocal::get, 0, TimeUnit.MILLISECONDS);
            threadLocal.set("changed-after-schedule");

            assertThat(scheduled.get(5, TimeUnit.SECONDS)).isEqualTo("scheduled");
        }
        finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void registryReplacesThreadLocalAccessorForSameKeyAndRejectsOverlappingContextAccessors() {
        ThreadLocal<String> original = new ThreadLocal<>();
        ThreadLocal<String> replacement = new ThreadLocal<>();
        ContextRegistry registry = new ContextRegistry()
                .registerThreadLocalAccessor(FIRST_KEY, original)
                .registerThreadLocalAccessor(FIRST_KEY, replacement);

        assertThat(registry.getThreadLocalAccessors()).hasSize(1);
        assertThat(registry.removeThreadLocalAccessor(FIRST_KEY)).isTrue();
        assertThat(registry.removeThreadLocalAccessor(FIRST_KEY)).isFalse();
        assertThatThrownBy(() -> registry.getThreadLocalAccessors().add(new StringThreadLocalAccessor("other")))
                .isInstanceOf(UnsupportedOperationException.class);

        ContextAccessor<?, ?> accessor = new TestContextAccessor();
        registry.registerContextAccessor(accessor);
        assertThat(registry.getContextAccessorForRead(new TestContext())).isSameAs(accessor);
        assertThat(registry.getContextAccessorForWrite(new TestContext())).isSameAs(accessor);
        assertThatThrownBy(() -> registry.registerContextAccessor(new TestContextAccessor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered accessor");
        assertThat(registry.removeContextAccessor(accessor)).isTrue();
        assertThatThrownBy(() -> registry.getContextAccessorForRead(new TestContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No ContextAccessor");
    }

    private static final class TestContext {
        private final Map<Object, Object> values = new LinkedHashMap<>();

        private TestContext put(Object key, Object value) {
            this.values.put(key, value);
            return this;
        }

        private Object get(Object key) {
            return this.values.get(key);
        }

        private Map<Object, Object> asMap() {
            return Collections.unmodifiableMap(this.values);
        }
    }

    private static final class TestContextAccessor implements ContextAccessor<TestContext, TestContext> {
        @Override
        public Class<? extends TestContext> readableType() {
            return TestContext.class;
        }

        @Override
        public void readValues(TestContext sourceContext, Predicate<Object> keyPredicate,
                Map<Object, Object> readValues) {
            sourceContext.values.forEach((key, value) -> {
                if (keyPredicate.test(key)) {
                    readValues.put(key, value);
                }
            });
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T readValue(TestContext sourceContext, Object key) {
            return (T) sourceContext.values.get(key);
        }

        @Override
        public Class<? extends TestContext> writeableType() {
            return TestContext.class;
        }

        @Override
        public TestContext writeValues(Map<Object, Object> valuesToWrite, TestContext targetContext) {
            valuesToWrite.forEach(targetContext::put);
            return targetContext;
        }
    }

    private static final class StringThreadLocalAccessor implements ThreadLocalAccessor<String> {
        private final String key;
        private final ThreadLocal<String> threadLocal = new ThreadLocal<>();

        private StringThreadLocalAccessor(String key) {
            this.key = key;
        }

        @Override
        public Object key() {
            return this.key;
        }

        @Override
        public String getValue() {
            return this.threadLocal.get();
        }

        @Override
        public void setValue(String value) {
            this.threadLocal.set(value);
        }

        @Override
        public void setValue() {
            this.threadLocal.remove();
        }
    }
}
