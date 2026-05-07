/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_timelimiter;

import io.github.resilience4j.core.ConfigurationNotFoundException;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.timelimiter.event.TimeLimiterEvent;
import io.github.resilience4j.timelimiter.event.TimeLimiterOnErrorEvent;
import io.github.resilience4j.timelimiter.event.TimeLimiterOnSuccessEvent;
import io.github.resilience4j.timelimiter.event.TimeLimiterOnTimeoutEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Resilience4j_timelimiterTest {

    @Test
    void configBuilderCreatesIndependentConfigurations() {
        TimeLimiterConfig defaults = TimeLimiterConfig.ofDefaults();
        TimeLimiterConfig custom = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(75))
            .cancelRunningFuture(false)
            .build();
        TimeLimiterConfig copy = TimeLimiterConfig.from(custom)
            .timeoutDuration(Duration.ofMillis(125))
            .build();

        assertThat(defaults.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(1));
        assertThat(defaults.shouldCancelRunningFuture()).isTrue();
        assertThat(custom.getTimeoutDuration()).isEqualTo(Duration.ofMillis(75));
        assertThat(custom.shouldCancelRunningFuture()).isFalse();
        assertThat(copy.getTimeoutDuration()).isEqualTo(Duration.ofMillis(125));
        assertThat(copy.shouldCancelRunningFuture()).isFalse();
        assertThat(copy.toString()).contains("timeoutDuration=PT0.125S", "cancelRunningFuture=false");
        assertThatThrownBy(() -> TimeLimiterConfig.custom().timeoutDuration(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("TimeoutDuration must not be null");
    }

    @Test
    void futureSupplierReturnsResultAndPublishesSuccessEvent() throws Exception {
        TimeLimiter timeLimiter = TimeLimiter.of("future-success", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(200))
            .build(), Map.of("component", "inventory"));
        List<TimeLimiterOnSuccessEvent> successes = new CopyOnWriteArrayList<>();
        List<TimeLimiterEvent> allEvents = new CopyOnWriteArrayList<>();
        timeLimiter.getEventPublisher()
            .onSuccess(successes::add)
            .onEvent(allEvents::add);

        String result = timeLimiter.executeFutureSupplier(() -> CompletableFuture.completedFuture("available"));

        assertThat(result).isEqualTo("available");
        assertThat(timeLimiter.getName()).isEqualTo("future-success");
        assertThat(timeLimiter.getTags()).containsEntry("component", "inventory");
        assertThat(successes).hasSize(1);
        assertThat(successes.get(0).getTimeLimiterName()).isEqualTo("future-success");
        assertThat(successes.get(0).getEventType()).isEqualTo(TimeLimiterEvent.Type.SUCCESS);
        assertThat(successes.get(0).getCreationTime()).isNotNull();
        assertThat(allEvents).extracting(TimeLimiterEvent::getEventType)
            .containsExactly(TimeLimiterEvent.Type.SUCCESS);
    }

    @Test
    void futureSupplierTimeoutCancelsRunningFutureAndPublishesTimeoutEvent() {
        TimeLimiter timeLimiter = TimeLimiter.of("future-timeout", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(25))
            .cancelRunningFuture(true)
            .build());
        AtomicBoolean cancelled = new AtomicBoolean();
        List<TimeLimiterOnTimeoutEvent> timeouts = new CopyOnWriteArrayList<>();
        timeLimiter.getEventPublisher().onTimeout(timeouts::add);

        assertThatThrownBy(() -> timeLimiter.executeFutureSupplier(() -> new TimeoutFuture<>(cancelled)))
            .isInstanceOf(TimeoutException.class)
            .hasMessage("TimeLimiter 'future-timeout' recorded a timeout exception.");

        assertThat(cancelled).isTrue();
        assertThat(timeouts).hasSize(1);
        assertThat(timeouts.get(0).getTimeLimiterName()).isEqualTo("future-timeout");
        assertThat(timeouts.get(0).getEventType()).isEqualTo(TimeLimiterEvent.Type.TIMEOUT);
    }

    @Test
    void futureSupplierTimeoutCanLeaveRunningFutureUncancelled() {
        TimeLimiter timeLimiter = TimeLimiter.of("future-no-cancel", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(25))
            .cancelRunningFuture(false)
            .build());
        AtomicBoolean cancelled = new AtomicBoolean();

        assertThatThrownBy(() -> TimeLimiter.decorateFutureSupplier(
            timeLimiter, () -> new TimeoutFuture<String>(cancelled)).call())
            .isInstanceOf(TimeoutException.class);

        assertThat(cancelled).isFalse();
    }

    @Test
    void futureSupplierUnwrapsExecutionFailureAndPublishesErrorEvent() {
        TimeLimiter timeLimiter = TimeLimiter.of(Duration.ofMillis(200));
        IOException failure = new IOException("backend failed");
        AtomicReference<TimeLimiterOnErrorEvent> error = new AtomicReference<>();
        timeLimiter.getEventPublisher().onError(error::set);

        assertThatThrownBy(() -> timeLimiter.executeFutureSupplier(() -> failedFuture(failure)))
            .isSameAs(failure);

        assertThat(error.get()).isNotNull();
        assertThat(error.get().getEventType()).isEqualTo(TimeLimiterEvent.Type.ERROR);
        assertThat(error.get().getThrowable()).isSameAs(failure);
    }

    @Test
    void completionStageSupplierCompletesSuccessfullyAndCancelsScheduledTimeout() throws Exception {
        TimeLimiter timeLimiter = TimeLimiter.of("stage-success", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(200))
            .build());
        ScheduledExecutorService scheduler = newScheduler();
        List<TimeLimiterEvent.Type> eventTypes = new CopyOnWriteArrayList<>();
        timeLimiter.getEventPublisher().onEvent(event -> eventTypes.add(event.getEventType()));
        try {
            Supplier<CompletionStage<String>> decorated = TimeLimiter.decorateCompletionStage(
                timeLimiter, scheduler, () -> CompletableFuture.completedFuture("ok"));

            String result = decorated.get().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertThat(result).isEqualTo("ok");
            assertThat(eventTypes).containsExactly(TimeLimiterEvent.Type.SUCCESS);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void completionStageSupplierPropagatesFailureAndPublishesErrorEvent() {
        TimeLimiter timeLimiter = TimeLimiter.of("stage-error", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(200))
            .build());
        IllegalStateException failure = new IllegalStateException("invalid state");
        ScheduledExecutorService scheduler = newScheduler();
        AtomicReference<TimeLimiterOnErrorEvent> error = new AtomicReference<>();
        timeLimiter.getEventPublisher().onError(error::set);
        try {
            CompletionStage<String> stage = timeLimiter.executeCompletionStage(
                scheduler, () -> CompletableFuture.failedFuture(failure));

            assertThatThrownBy(() -> stage.toCompletableFuture().get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);
            assertThat(error.get()).isNotNull();
            assertThat(error.get().getTimeLimiterName()).isEqualTo("stage-error");
            assertThat(error.get().getThrowable()).isSameAs(failure);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void completionStageSupplierUnwrapsCompletionExceptionCauseForErrorEvent() {
        TimeLimiter timeLimiter = TimeLimiter.of(
            "stage-completion-exception",
            TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(200))
                .build());
        IllegalArgumentException failure = new IllegalArgumentException("wrapped failure");
        CompletableFuture<String> failedStage = new CompletableFuture<>();
        ScheduledExecutorService scheduler = newScheduler();
        AtomicReference<TimeLimiterOnErrorEvent> error = new AtomicReference<>();
        timeLimiter.getEventPublisher().onError(error::set);
        failedStage.completeExceptionally(new CompletionException(failure));
        try {
            CompletionStage<String> stage = timeLimiter.executeCompletionStage(
                scheduler, () -> failedStage);

            assertThatThrownBy(() -> stage.toCompletableFuture().get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);
            assertThat(error.get()).isNotNull();
            assertThat(error.get().getTimeLimiterName()).isEqualTo("stage-completion-exception");
            assertThat(error.get().getThrowable()).isSameAs(failure);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void completionStageSupplierTimesOutIncompleteStageAndPublishesTimeoutEvent() {
        TimeLimiter timeLimiter = TimeLimiter.of("stage-timeout", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(30))
            .build());
        ScheduledExecutorService scheduler = newScheduler();
        List<TimeLimiterOnTimeoutEvent> timeouts = new CopyOnWriteArrayList<>();
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();
        timeLimiter.getEventPublisher().onTimeout(timeouts::add);
        try {
            CompletionStage<String> decorated = timeLimiter.executeCompletionStage(
                scheduler, () -> neverCompletes);

            assertThatThrownBy(() -> decorated.toCompletableFuture().get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class)
                .hasRootCauseMessage("TimeLimiter 'stage-timeout' recorded a timeout exception.");
            assertThat(neverCompletes).isCompletedExceptionally();
            assertThat(timeouts).hasSize(1);
            assertThat(timeouts.get(0).getTimeLimiterName()).isEqualTo("stage-timeout");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void registryCreatesCachesTagsAndPublishesLifecycleEvents() {
        TimeLimiterConfig fastConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(90))
            .cancelRunningFuture(false)
            .build();
        TimeLimiterRegistry registry = TimeLimiterRegistry.of(
            Map.of("fast", fastConfig),
            Map.of("scope", "registry", "shared", "from-registry"));
        List<String> lifecycleEvents = new CopyOnWriteArrayList<>();
        registry.getEventPublisher()
            .onEntryAdded(event -> lifecycleEvents.add("added:" + event.getAddedEntry().getName()))
            .onEntryReplaced(event -> lifecycleEvents.add("replaced:" + event.getNewEntry().getName()))
            .onEntryRemoved(event -> lifecycleEvents.add("removed:" + event.getRemovedEntry().getName()));

        TimeLimiter created = registry.timeLimiter("checkout", "fast",
            Map.of("team", "payments", "shared", "from-instance"));
        TimeLimiter cached = registry.timeLimiter("checkout");
        TimeLimiter replacement = TimeLimiter.of("checkout", TimeLimiterConfig.ofDefaults());

        assertThat(cached).isSameAs(created);
        assertThat(created.getTimeLimiterConfig()).isSameAs(fastConfig);
        assertThat(created.getTags())
            .containsEntry("scope", "registry")
            .containsEntry("team", "payments")
            .containsEntry("shared", "from-instance");
        assertThat(registry.find("checkout")).contains(created);
        assertThat(registry.getAllTimeLimiters()).containsExactly(created);
        assertThat(registry.replace("checkout", replacement)).contains(created);
        assertThat(registry.remove("checkout")).contains(replacement);
        assertThat(registry.find("checkout")).isEmpty();
        assertThat(lifecycleEvents).containsExactly(
            "added:checkout", "replaced:checkout", "removed:checkout");
        assertThatThrownBy(() -> registry.timeLimiter("missing", "does-not-exist"))
            .isInstanceOf(ConfigurationNotFoundException.class);
    }

    @Test
    void registryCreatesTimeLimiterFromConfigSupplierOnlyWhenEntryIsAbsent() {
        TimeLimiterConfig suppliedConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(150))
            .cancelRunningFuture(false)
            .build();
        TimeLimiterRegistry registry = TimeLimiterRegistry.ofDefaults();
        AtomicInteger supplierCalls = new AtomicInteger();

        TimeLimiter created = registry.timeLimiter("shipping", () -> {
            supplierCalls.incrementAndGet();
            return suppliedConfig;
        });
        TimeLimiter cached = registry.timeLimiter("shipping", () -> {
            supplierCalls.incrementAndGet();
            return TimeLimiterConfig.ofDefaults();
        });

        assertThat(cached).isSameAs(created);
        assertThat(supplierCalls.get()).isEqualTo(1);
        assertThat(created.getTimeLimiterConfig()).isSameAs(suppliedConfig);
    }

    private static ScheduledExecutorService newScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "resilience4j-time-limiter-test-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
    }

    private static final class TimeoutFuture<T> implements Future<T> {
        private final AtomicBoolean cancelled;

        private TimeoutFuture(AtomicBoolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled.set(true);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public T get() {
            throw new CancellationException("This test future only supports timed get");
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws TimeoutException {
            throw new TimeoutException("simulated timeout");
        }
    }
}
