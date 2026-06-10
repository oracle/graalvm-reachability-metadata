/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_timelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.core.registry.RegistryEvent;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.timelimiter.event.TimeLimiterEvent;
import io.github.resilience4j.timelimiter.event.TimeLimiterOnErrorEvent;
import io.github.resilience4j.timelimiter.event.TimeLimiterOnTimeoutEvent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Resilience4j_timelimiterTest {
    @Test
    void createsTimeLimiterFromCustomAndCopiedConfiguration() {
        TimeLimiterConfig baseConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(250))
            .cancelRunningFuture(false)
            .build();
        TimeLimiterConfig copiedConfig = TimeLimiterConfig.from(baseConfig)
            .timeoutDuration(Duration.ofMillis(500))
            .build();

        TimeLimiter taggedTimeLimiter = TimeLimiter.of("catalog", copiedConfig, Map.of("component", "search"));
        TimeLimiter durationTimeLimiter = TimeLimiter.of(Duration.ofMillis(75));
        TimeLimiter defaultTimeLimiter = TimeLimiter.ofDefaults();

        assertThat(baseConfig.getTimeoutDuration()).isEqualTo(Duration.ofMillis(250));
        assertThat(baseConfig.shouldCancelRunningFuture()).isFalse();
        assertThat(copiedConfig.getTimeoutDuration()).isEqualTo(Duration.ofMillis(500));
        assertThat(copiedConfig.shouldCancelRunningFuture()).isFalse();
        assertThat(copiedConfig.toString())
            .contains("timeoutDuration=PT0.5S")
            .contains("cancelRunningFuture=false");
        assertThat(taggedTimeLimiter.getName()).isEqualTo("catalog");
        assertThat(taggedTimeLimiter.getTags()).containsEntry("component", "search");
        assertThat(taggedTimeLimiter.getTimeLimiterConfig()).isSameAs(copiedConfig);
        assertThat(durationTimeLimiter.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofMillis(75));
        assertThat(defaultTimeLimiter.getName()).isEqualTo(TimeLimiter.DEFAULT_NAME);
        assertThat(defaultTimeLimiter.getTimeLimiterConfig().getTimeoutDuration().toNanos()).isPositive();
        assertThat(defaultTimeLimiter.getTimeLimiterConfig().shouldCancelRunningFuture()).isTrue();
    }

    @Test
    void decoratesFutureSupplierAndPublishesSuccessEvents() throws Exception {
        TimeLimiter timeLimiter = TimeLimiter.of("future-success", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(1))
            .build());
        List<TimeLimiterEvent.Type> eventTypes = new ArrayList<>();
        List<String> successNames = new ArrayList<>();
        timeLimiter.getEventPublisher().onEvent(event -> eventTypes.add(event.getEventType()));
        timeLimiter.getEventPublisher().onSuccess(event -> successNames.add(event.getTimeLimiterName()));

        String result = TimeLimiter.decorateFutureSupplier(
            timeLimiter,
            () -> CompletableFuture.completedFuture("ready"))
            .call();

        assertThat(result).isEqualTo("ready");
        assertThat(eventTypes).containsExactly(TimeLimiterEvent.Type.SUCCESS);
        assertThat(successNames).containsExactly("future-success");
    }

    @Test
    void cancelsFutureSupplierOnTimeoutAndPublishesTimeoutEvents() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(10))
            .cancelRunningFuture(true)
            .build();
        TimeLimiter timeLimiter = TimeLimiter.of("future-timeout", config);
        TimeoutOnlyFuture<String> future = new TimeoutOnlyFuture<>();
        List<TimeLimiterEvent.Type> eventTypes = new ArrayList<>();
        List<TimeLimiterOnTimeoutEvent> timeoutEvents = new ArrayList<>();
        timeLimiter.getEventPublisher().onEvent(event -> eventTypes.add(event.getEventType()));
        timeLimiter.getEventPublisher().onTimeout(timeoutEvents::add);

        assertThatThrownBy(() -> timeLimiter.executeFutureSupplier(() -> future))
            .isInstanceOf(TimeoutException.class)
            .hasMessageContaining("future-timeout");

        assertThat(future.isCancelled()).isTrue();
        assertThat(future.wasCancelledWithInterrupt()).isTrue();
        assertThat(eventTypes).containsExactly(TimeLimiterEvent.Type.TIMEOUT);
        assertThat(timeoutEvents).hasSize(1);
        assertThat(timeoutEvents.get(0).getTimeLimiterName()).isEqualTo("future-timeout");
        assertThat(timeoutEvents.get(0).getEventType()).isEqualTo(TimeLimiterEvent.Type.TIMEOUT);
        assertThat(timeoutEvents.get(0).getCreationTime()).isNotNull();
        assertThat(timeoutEvents.get(0).toString()).contains("future-timeout");
    }

    @Test
    void leavesFutureSupplierRunningOnTimeoutWhenCancellationIsDisabled() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(10))
            .cancelRunningFuture(false)
            .build();
        TimeLimiter timeLimiter = TimeLimiter.of("future-timeout-without-cancel", config);
        TimeoutOnlyFuture<String> future = new TimeoutOnlyFuture<>();

        assertThatThrownBy(() -> timeLimiter.executeFutureSupplier(() -> future))
            .isInstanceOf(TimeoutException.class)
            .hasMessageContaining("future-timeout-without-cancel");

        assertThat(future.isCancelled()).isFalse();
        assertThat(future.wasCancelledWithInterrupt()).isFalse();
    }

    @Test
    void unwrapsFutureSupplierFailureAndPublishesErrorEvents() {
        TimeLimiter timeLimiter = TimeLimiter.of("future-error", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(1))
            .build());
        IllegalStateException failure = new IllegalStateException("backend rejected request");
        FailedFuture<String> future = new FailedFuture<>(failure);
        List<TimeLimiterEvent.Type> eventTypes = new ArrayList<>();
        List<TimeLimiterOnErrorEvent> errorEvents = new ArrayList<>();
        timeLimiter.getEventPublisher().onEvent(event -> eventTypes.add(event.getEventType()));
        timeLimiter.getEventPublisher().onError(errorEvents::add);

        assertThatThrownBy(() -> timeLimiter.executeFutureSupplier(() -> future))
            .isSameAs(failure);

        assertThat(eventTypes).containsExactly(TimeLimiterEvent.Type.ERROR);
        assertThat(errorEvents).hasSize(1);
        assertThat(errorEvents.get(0).getTimeLimiterName()).isEqualTo("future-error");
        assertThat(errorEvents.get(0).getEventType()).isEqualTo(TimeLimiterEvent.Type.ERROR);
        assertThat(errorEvents.get(0).getThrowable()).isSameAs(failure);
        assertThat(errorEvents.get(0).toString()).contains("future-error", "backend rejected request");
    }

    @Test
    void decoratesCompletionStageAndCancelsScheduledTimeoutAfterSuccess() throws Exception {
        TimeLimiter timeLimiter = TimeLimiter.of("stage-success", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(1))
            .build());
        List<TimeLimiterEvent.Type> eventTypes = new ArrayList<>();
        List<String> successNames = new ArrayList<>();
        timeLimiter.getEventPublisher().onEvent(event -> eventTypes.add(event.getEventType()));
        timeLimiter.getEventPublisher().onSuccess(event -> successNames.add(event.getTimeLimiterName()));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CompletionStage<String> resultStage = TimeLimiter.decorateCompletionStage(
                timeLimiter,
                scheduler,
                () -> CompletableFuture.completedFuture("stage-ready"))
                .get();

            assertThat(resultStage.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("stage-ready");
            assertThat(eventTypes).containsExactly(TimeLimiterEvent.Type.SUCCESS);
            assertThat(successNames).containsExactly("stage-success");
        } finally {
            scheduler.shutdownNow();
            assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void unwrapsCompletionStageFailureAndPublishesErrorEvents() throws Exception {
        TimeLimiter timeLimiter = TimeLimiter.of("stage-error", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(1))
            .build());
        IllegalArgumentException failure = new IllegalArgumentException("stage backend rejected request");
        CompletableFuture<String> failedStage = new CompletableFuture<>();
        failedStage.completeExceptionally(new CompletionException(failure));
        List<TimeLimiterEvent.Type> eventTypes = new ArrayList<>();
        List<TimeLimiterOnErrorEvent> errorEvents = new ArrayList<>();
        timeLimiter.getEventPublisher().onEvent(event -> eventTypes.add(event.getEventType()));
        timeLimiter.getEventPublisher().onError(errorEvents::add);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CompletionStage<String> resultStage = timeLimiter.executeCompletionStage(scheduler, () -> failedStage);

            assertThatThrownBy(() -> resultStage.toCompletableFuture().get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(throwable -> assertThat(throwable.getCause()).isSameAs(failure));
            assertThat(eventTypes).containsExactly(TimeLimiterEvent.Type.ERROR);
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.get(0).getTimeLimiterName()).isEqualTo("stage-error");
            assertThat(errorEvents.get(0).getEventType()).isEqualTo(TimeLimiterEvent.Type.ERROR);
            assertThat(errorEvents.get(0).getThrowable()).isSameAs(failure);
            assertThat(errorEvents.get(0).toString()).contains("stage-error", "stage backend rejected request");
        } finally {
            scheduler.shutdownNow();
            assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void completesCompletionStageExceptionallyOnTimeoutAndPublishesTimeoutEvents() throws Exception {
        TimeLimiter timeLimiter = TimeLimiter.of("stage-timeout", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(20))
            .build());
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();
        List<TimeLimiterEvent.Type> eventTypes = new ArrayList<>();
        List<TimeLimiterOnTimeoutEvent> timeoutEvents = new ArrayList<>();
        timeLimiter.getEventPublisher().onEvent(event -> eventTypes.add(event.getEventType()));
        timeLimiter.getEventPublisher().onTimeout(timeoutEvents::add);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CompletionStage<String> resultStage = timeLimiter.executeCompletionStage(scheduler, () -> neverCompletes);

            assertThatThrownBy(() -> resultStage.toCompletableFuture().get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class)
                .hasRootCauseMessage("TimeLimiter 'stage-timeout' recorded a timeout exception.");
            assertThat(eventTypes).containsExactly(TimeLimiterEvent.Type.TIMEOUT);
            assertThat(timeoutEvents).hasSize(1);
            assertThat(timeoutEvents.get(0).getTimeLimiterName()).isEqualTo("stage-timeout");
            assertThat(neverCompletes.isCompletedExceptionally()).isTrue();
        } finally {
            scheduler.shutdownNow();
            assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void registryCreatesCachesReplacesAndRemovesTimeLimiters() {
        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(300))
            .build();
        TimeLimiterConfig namedConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(50))
            .cancelRunningFuture(false)
            .build();
        TimeLimiterRegistry registry = TimeLimiterRegistry.of(
            Map.of("fast", namedConfig),
            Map.of("application", "checkout"));
        registry.addConfiguration("standard", defaultConfig);
        List<RegistryEvent.Type> registryEvents = new ArrayList<>();
        List<String> eventNames = new ArrayList<>();
        registry.getEventPublisher().onEvent(event -> registryEvents.add(event.getEventType()));
        registry.getEventPublisher().onEntryAdded(event -> eventNames.add("added:" + event.getAddedEntry().getName()));
        registry.getEventPublisher().onEntryReplaced(event -> eventNames.add("replaced:"
            + event.getOldEntry().getName() + ":" + event.getNewEntry().getName()));
        registry.getEventPublisher().onEntryRemoved(event -> eventNames.add(
            "removed:" + event.getRemovedEntry().getName()));

        TimeLimiter created = registry.timeLimiter("orders", "fast", Map.of("kind", "client"));
        TimeLimiter cached = registry.timeLimiter("orders", () -> defaultConfig);
        TimeLimiter replacement = TimeLimiter.of("orders", defaultConfig, Map.of("kind", "replacement"));
        Optional<TimeLimiter> replaced = registry.replace("orders", replacement);
        Optional<TimeLimiter> removed = registry.remove("orders");

        assertThat(registry.getTags()).containsEntry("application", "checkout");
        assertThat(registry.getConfiguration("fast").orElseThrow()).isSameAs(namedConfig);
        assertThat(created).isSameAs(cached);
        assertThat(created.getTimeLimiterConfig()).isSameAs(namedConfig);
        assertThat(created.getTags()).containsEntry("kind", "client");
        assertThat(replaced.orElseThrow()).isSameAs(created);
        assertThat(removed.orElseThrow()).isSameAs(replacement);
        assertThat(registry.find("orders")).isEmpty();
        assertThat(registry.getAllTimeLimiters()).isEmpty();
        assertThat(registry.removeConfiguration("fast")).isSameAs(namedConfig);
        assertThat(registryEvents).containsExactly(
            RegistryEvent.Type.ADDED,
            RegistryEvent.Type.REPLACED,
            RegistryEvent.Type.REMOVED);
        assertThat(eventNames).containsExactly(
            "added:orders",
            "replaced:orders:orders",
            "removed:orders");
    }

    private static final class TimeoutOnlyFuture<T> implements Future<T> {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile boolean cancelledWithInterrupt;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelledWithInterrupt = mayInterruptIfRunning;
            cancelled.set(true);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return cancelled.get();
        }

        @Override
        public T get() {
            throw new UnsupportedOperationException("Only timed get is expected");
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws TimeoutException {
            throw new TimeoutException("simulated timeout after " + timeout + " " + unit);
        }

        boolean wasCancelledWithInterrupt() {
            return cancelledWithInterrupt;
        }
    }

    private static final class FailedFuture<T> implements Future<T> {
        private final Throwable failure;

        private FailedFuture(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() throws ExecutionException {
            throw new ExecutionException(failure);
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws ExecutionException {
            throw new ExecutionException(failure);
        }
    }
}
