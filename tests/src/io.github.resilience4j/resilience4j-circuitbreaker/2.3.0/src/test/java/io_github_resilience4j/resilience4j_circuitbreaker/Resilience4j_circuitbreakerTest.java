/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_circuitbreaker;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.StateTransition;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowSynchronizationStrategy;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.TransitionCheckResult;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent.Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Resilience4j_circuitbreakerTest {
    @Test
    void countBasedCircuitBreakerRecordsMetricsEventsAndOpensOnConfiguredThresholds() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindow(4, 4, SlidingWindowType.COUNT_BASED, SlidingWindowSynchronizationStrategy.SYNCHRONIZED)
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofMillis(10))
                .waitDurationInOpenState(Duration.ofMillis(100))
                .recordExceptions(IllegalStateException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .recordResult(result -> "retry".equals(result))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("orders", config, Map.of("component", "checkout"));
        List<Type> eventTypes = new ArrayList<>();
        List<Float> failureRates = new ArrayList<>();
        List<Float> slowCallRates = new ArrayList<>();
        List<StateTransition> transitions = new ArrayList<>();

        circuitBreaker.getEventPublisher().onEvent(event -> eventTypes.add(event.getEventType()));
        circuitBreaker.getEventPublisher().onFailureRateExceeded(event -> failureRates.add(event.getFailureRate()));
        circuitBreaker.getEventPublisher().onSlowCallRateExceeded(event -> slowCallRates.add(event.getSlowCallRate()));
        circuitBreaker.getEventPublisher().onStateTransition(event -> transitions.add(event.getStateTransition()));

        circuitBreaker.onSuccess(20, TimeUnit.MILLISECONDS);
        circuitBreaker.onError(30, TimeUnit.MILLISECONDS, new IllegalStateException("recorded"));
        circuitBreaker.onError(5, TimeUnit.MILLISECONDS, new IllegalArgumentException("ignored"));
        circuitBreaker.onResult(5, TimeUnit.MILLISECONDS, "retry");
        circuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);

        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(metrics.getNumberOfBufferedCalls()).isEqualTo(4);
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(2);
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(metrics.getNumberOfSlowCalls()).isEqualTo(2);
        assertThat(metrics.getNumberOfSlowSuccessfulCalls()).isEqualTo(1);
        assertThat(metrics.getNumberOfSlowFailedCalls()).isEqualTo(1);
        assertThat(metrics.getFailureRate()).isEqualTo(50.0f);
        assertThat(metrics.getSlowCallRate()).isEqualTo(50.0f);
        assertThat(circuitBreaker.getTags()).containsEntry("component", "checkout");

        assertThat(eventTypes).contains(Type.SUCCESS, Type.ERROR, Type.IGNORED_ERROR, Type.FAILURE_RATE_EXCEEDED,
                Type.SLOW_CALL_RATE_EXCEEDED, Type.STATE_TRANSITION);
        assertThat(failureRates).containsExactly(50.0f);
        assertThat(slowCallRates).containsExactly(50.0f);
        assertThat(transitions).contains(StateTransition.CLOSED_TO_OPEN);

        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> circuitBreaker.executeRunnable(() -> { }))
                .satisfies(exception -> assertThat(exception.getCausingCircuitBreakerName()).isEqualTo("orders"));
        assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    void decoratorsApplyPermissionsAndRecordCallableSupplierRunnableAndCheckedFailures() throws Throwable {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("decorators", config);
        AtomicInteger invocations = new AtomicInteger();

        Supplier<String> supplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            invocations.incrementAndGet();
            return "value";
        });
        Callable<String> callable = CircuitBreaker.decorateCallable(circuitBreaker, () -> {
            invocations.incrementAndGet();
            throw new IOException("io failure");
        });

        assertThat(supplier.get()).isEqualTo("value");
        assertThatThrownBy(callable::call).isInstanceOf(IOException.class).hasMessage("io failure");

        assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(invocations).hasValue(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);

        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> CircuitBreaker.decorateRunnable(circuitBreaker, invocations::incrementAndGet).run());
        assertThat(invocations).hasValue(2);

        circuitBreaker.reset();
        assertThat(circuitBreaker.executeCheckedSupplier(() -> "checked")).isEqualTo("checked");
        assertThatThrownBy(() -> circuitBreaker.executeCheckedRunnable(() -> {
            throw new IOException("checked runnable failure");
        })).isInstanceOf(IOException.class).hasMessage("checked runnable failure");
        assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
    }

    @Test
    void transitionOnResultCanOpenCircuitAfterSuccessfulCall() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(100.0f)
                .transitionOnResult(result -> result.isLeft() && "maintenance".equals(result.getLeft())
                        ? TransitionCheckResult.transitionToOpenAndWaitFor(Duration.ofSeconds(5))
                        : TransitionCheckResult.noTransition())
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("transition-on-result", config);

        assertThat(circuitBreaker.executeSupplier(() -> "ready")).isEqualTo("ready");
        assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);

        assertThat(circuitBreaker.executeSupplier(() -> "maintenance")).isEqualTo("maintenance");

        assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> circuitBreaker.executeSupplier(() -> "blocked"));
    }

    @Test
    void halfOpenStateClosesAfterPermittedSuccessesAndReopensAfterFailureRateBreach() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .permittedNumberOfCallsInHalfOpenState(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("half-open", config);
        List<StateTransition> transitions = new ArrayList<>();
        circuitBreaker.getEventPublisher().onStateTransition(event -> transitions.add(event.getStateTransition()));

        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();

        assertThat(circuitBreaker.executeSupplier(() -> "first")).isEqualTo("first");
        assertThat(circuitBreaker.getState()).isEqualTo(State.HALF_OPEN);
        assertThat(circuitBreaker.executeSupplier(() -> "second")).isEqualTo("second");
        assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);

        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
        circuitBreaker.releasePermission();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        circuitBreaker.onSuccess(1, TimeUnit.MILLISECONDS);
        circuitBreaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("half-open failure"));

        assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(transitions).contains(StateTransition.OPEN_TO_HALF_OPEN, StateTransition.HALF_OPEN_TO_CLOSED,
                StateTransition.HALF_OPEN_TO_OPEN);
    }

    @Test
    void specialStatesHandlePermissionsAndMetricsDifferently() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(1.0f)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("special-states", config);

        circuitBreaker.transitionToMetricsOnlyState();
        assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> {
            throw new IllegalStateException("recorded only");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> {
            throw new IllegalStateException("recorded only again");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(circuitBreaker.getState()).isEqualTo(State.METRICS_ONLY);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);

        circuitBreaker.transitionToForcedOpenState();
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> circuitBreaker.acquirePermission());

        CircuitBreaker disabledBreaker = CircuitBreaker.of("disabled", config);
        disabledBreaker.transitionToDisabledState();
        assertThat(disabledBreaker.tryAcquirePermission()).isTrue();
        assertThatThrownBy(() -> disabledBreaker.executeSupplier(() -> {
            throw new IllegalStateException("not recorded while disabled");
        })).isInstanceOf(IllegalStateException.class);
        disabledBreaker.executeRunnable(() -> { });
        assertThat(disabledBreaker.getState()).isEqualTo(State.DISABLED);
        assertThat(disabledBreaker.getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void asynchronousCompletionStagesAndFuturesAreRecordedWhenTheyComplete() throws Exception {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .build();
        CircuitBreaker stageBreaker = CircuitBreaker.of("stages", config);

        CompletionStage<String> successfulStage = stageBreaker.executeCompletionStage(
                () -> CompletableFuture.completedFuture("stage-value"));
        assertThat(successfulStage.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("stage-value");

        CompletionStage<String> failedStage = CircuitBreaker.decorateCompletionStage(stageBreaker,
                () -> CompletableFuture.<String>failedFuture(new IllegalStateException("stage failure"))).get();
        assertThatThrownBy(() -> failedStage.toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(stageBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(stageBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(stageBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);

        CircuitBreaker futureBreaker = CircuitBreaker.of("futures", config);
        Future<String> successfulFuture = futureBreaker.decorateFuture(
                () -> CompletableFuture.completedFuture("future-value")).get();
        assertThat(successfulFuture.get(1, TimeUnit.SECONDS)).isEqualTo("future-value");

        Future<String> failedFuture = CircuitBreaker.decorateFuture(futureBreaker,
                () -> CompletableFuture.<String>failedFuture(new IllegalArgumentException("future failure"))).get();
        assertThatThrownBy(() -> failedFuture.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(futureBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(futureBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(futureBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void registryReusesInstancesAndAppliesNamedConfigurationsAndTags() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(75.0f)
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(3)
                .build();
        CircuitBreakerConfig strictConfig = CircuitBreakerConfig.from(defaultConfig)
                .failureRateThreshold(25.0f)
                .slidingWindow(5, 2, SlidingWindowType.COUNT_BASED, SlidingWindowSynchronizationStrategy.LOCK_FREE)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.custom()
                .withCircuitBreakerConfig(defaultConfig)
                .addCircuitBreakerConfig("strict", strictConfig)
                .withTags(Map.of("environment", "test"))
                .build();

        CircuitBreaker strict = registry.circuitBreaker("strict-service", "strict", Map.of("backend", "orders"));
        CircuitBreaker strictAgain = registry.circuitBreaker("strict-service");
        CircuitBreaker fallback = registry.circuitBreaker("fallback-service");

        assertThat(strictAgain).isSameAs(strict);
        assertThat(strict.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(25.0f);
        assertThat(strict.getCircuitBreakerConfig().getSlidingWindowType()).isEqualTo(SlidingWindowType.COUNT_BASED);
        assertThat(strict.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(2);
        assertThat(strict.getTags()).containsEntry("backend", "orders");
        assertThat(fallback.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(75.0f);
        assertThat(fallback.getCircuitBreakerConfig().getSlidingWindowType()).isEqualTo(SlidingWindowType.TIME_BASED);
        assertThat(registry.getAllCircuitBreakers())
                .extracting(CircuitBreaker::getName)
                .containsExactlyInAnyOrder("strict-service", "fallback-service");
    }
}
