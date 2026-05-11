/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_reactor;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.micrometer.Timer;
import io.github.resilience4j.micrometer.event.TimerEvent;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.IllegalPublisherException;
import io.github.resilience4j.reactor.ReactorOperatorFallbackDecorator;
import io.github.resilience4j.reactor.adapter.ReactorAdapter;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.micrometer.operator.TimerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Resilience4j_reactorTest {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(2);

    @Test
    void circuitBreakerOperatorRecordsReactiveSuccessAndRejectsOpenCircuit() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("reactor-circuit-breaker", config);

        String result = Mono.just("accepted")
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .block(BLOCK_TIMEOUT);

        assertThat(result).isEqualTo("accepted");
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);

        circuitBreaker.transitionToOpenState();
        assertThatThrownBy(() -> Mono.just("rejected")
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .block(BLOCK_TIMEOUT))
            .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void fallbackDecoratorRecoversFromCircuitBreakerAndTimeLimiterFailures() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("fallback-circuit-breaker");
        circuitBreaker.transitionToOpenState();
        AtomicInteger subscriptions = new AtomicInteger();

        String openCircuitFallback = Mono.fromSupplier(() -> {
                subscriptions.incrementAndGet();
                return "primary";
            })
            .transformDeferred(ReactorOperatorFallbackDecorator.decorateCircuitBreaker(
                CircuitBreakerOperator.of(circuitBreaker), Mono.just("circuit-fallback")))
            .block(BLOCK_TIMEOUT);

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(50))
            .build();
        TimeLimiter timeLimiter = TimeLimiter.of("fallback-time-limiter", timeLimiterConfig);
        String timeoutFallback = Mono.<String>never()
            .transformDeferred(ReactorOperatorFallbackDecorator.decorateTimeLimiter(
                TimeLimiterOperator.of(timeLimiter), Mono.just("timeout-fallback")))
            .block(BLOCK_TIMEOUT);

        assertThat(openCircuitFallback).isEqualTo("circuit-fallback");
        assertThat(timeoutFallback).isEqualTo("timeout-fallback");
        assertThat(subscriptions).hasValue(0);
    }

    @Test
    void bulkheadOperatorReleasesPermitsAndRejectsWhenFull() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ZERO)
            .build();
        Bulkhead bulkhead = Bulkhead.of("reactor-bulkhead", config);

        List<Integer> values = Flux.just(1, 2, 3)
            .transformDeferred(BulkheadOperator.of(bulkhead))
            .collectList()
            .block(BLOCK_TIMEOUT);

        assertThat(values).containsExactly(1, 2, 3);
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);

        bulkhead.acquirePermission();
        try {
            assertThatThrownBy(() -> Mono.just("blocked")
                .transformDeferred(BulkheadOperator.of(bulkhead))
                .block(BLOCK_TIMEOUT))
                .isInstanceOf(BulkheadFullException.class);
        } finally {
            bulkhead.releasePermission();
        }
    }

    @Test
    void bulkheadOperatorReleasesPermitWhenSubscriptionIsCancelled() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ZERO)
            .build();
        Bulkhead bulkhead = Bulkhead.of("reactor-cancelled-bulkhead", config);

        Disposable subscription = Mono.<String>never()
            .transformDeferred(BulkheadOperator.of(bulkhead))
            .subscribe();

        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isZero();
        subscription.dispose();
        assertThat(subscription.isDisposed()).isTrue();
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);

        String value = Mono.just("after-cancel")
            .transformDeferred(BulkheadOperator.of(bulkhead))
            .block(BLOCK_TIMEOUT);

        assertThat(value).isEqualTo("after-cancel");
    }

    @Test
    void rateLimiterOperatorConsumesWeightedPermitsAndFailsFastWhenExhausted() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(2)
            .limitRefreshPeriod(Duration.ofHours(1))
            .timeoutDuration(Duration.ZERO)
            .build();
        RateLimiter rateLimiter = RateLimiter.of("reactor-rate-limiter", config);

        List<String> permitted = Flux.just("alpha", "beta")
            .transformDeferred(RateLimiterOperator.of(rateLimiter, 2))
            .collectList()
            .block(BLOCK_TIMEOUT);

        assertThat(permitted).containsExactly("alpha", "beta");
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isZero();
        assertThatThrownBy(() -> Mono.just("gamma")
            .transformDeferred(RateLimiterOperator.of(rateLimiter))
            .block(BLOCK_TIMEOUT))
            .isInstanceOf(RequestNotPermitted.class);
    }

    @Test
    void rateLimiterOperatorDelaysSubscriptionUntilPermitIsRefreshed() throws Exception {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofMillis(100))
            .timeoutDuration(Duration.ofMillis(500))
            .build();
        RateLimiter rateLimiter = RateLimiter.of("reactor-waiting-rate-limiter", config);
        AtomicInteger subscriptions = new AtomicInteger();

        assertThat(rateLimiter.acquirePermission()).isTrue();
        CompletableFuture<String> future = Mono.fromSupplier(() -> {
                subscriptions.incrementAndGet();
                return "after-refresh";
            })
            .transformDeferred(RateLimiterOperator.of(rateLimiter))
            .toFuture();

        try {
            assertThat(subscriptions).hasValue(0);
            assertThat(future).isNotDone();
            assertThat(future.get(BLOCK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .isEqualTo("after-refresh");
            assertThat(subscriptions).hasValue(1);
        } finally {
            future.cancel(true);
        }
    }

    @Test
    void retryOperatorResubscribesMonoUntilExceptionEventuallySucceeds() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ZERO)
            .retryExceptions(IllegalStateException.class)
            .build();
        Retry retry = Retry.of("reactor-exception-retry", config);
        AtomicInteger attempts = new AtomicInteger();

        String value = Mono.fromSupplier(() -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("not yet");
                }
                return "ready";
            })
            .transformDeferred(RetryOperator.of(retry))
            .block(BLOCK_TIMEOUT);

        assertThat(value).isEqualTo("ready");
        assertThat(attempts).hasValue(3);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
    }

    @Test
    void retryOperatorCanRetryFluxBasedOnResultPredicate() {
        RetryConfig config = RetryConfig.<String>custom()
            .maxAttempts(2)
            .waitDuration(Duration.ZERO)
            .retryOnResult("retry"::equals)
            .build();
        Retry retry = Retry.of("reactor-result-retry", config);
        AtomicInteger subscriptions = new AtomicInteger();

        List<String> values = Flux.defer(() -> subscriptions.incrementAndGet() == 1
                ? Flux.just("retry")
                : Flux.just("done"))
            .transformDeferred(RetryOperator.of(retry))
            .collectList()
            .block(BLOCK_TIMEOUT);

        assertThat(values).containsExactly("done");
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void timeLimiterOperatorTimesOutSlowMonoAndPublishesSuccessForFastFlux() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(50))
            .build();
        TimeLimiter timeLimiter = TimeLimiter.of("reactor-time-limiter", config);
        AtomicInteger timeoutEvents = new AtomicInteger();
        AtomicInteger successEvents = new AtomicInteger();
        timeLimiter.getEventPublisher()
            .onTimeout(event -> timeoutEvents.incrementAndGet())
            .onSuccess(event -> successEvents.incrementAndGet());

        assertThatThrownBy(() -> Mono.never()
            .transformDeferred(TimeLimiterOperator.of(timeLimiter))
            .block(BLOCK_TIMEOUT))
            .hasCauseInstanceOf(TimeoutException.class);

        List<Integer> values = Flux.just(1, 2)
            .transformDeferred(TimeLimiterOperator.of(timeLimiter))
            .collectList()
            .block(BLOCK_TIMEOUT);

        assertThat(values).containsExactly(1, 2);
        assertThat(timeoutEvents).hasValue(1);
        assertThat(successEvents).hasValue(3);
    }

    @Test
    void timerOperatorRecordsReactiveSuccessAndFailureEvents() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            Timer timer = Timer.of("reactor-timer", meterRegistry);
            List<TimerEvent.Type> eventTypes = new ArrayList<>();
            timer.getEventPublisher()
                .onStart(event -> eventTypes.add(event.getEventType()))
                .onSuccess(event -> eventTypes.add(event.getEventType()))
                .onFailure(event -> eventTypes.add(event.getEventType()));

            String value = Mono.just("timed")
                .transformDeferred(TimerOperator.of(timer))
                .block(BLOCK_TIMEOUT);
            assertThatThrownBy(() -> Mono.<String>error(new IllegalArgumentException("boom"))
                .transformDeferred(TimerOperator.of(timer))
                .block(BLOCK_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);

            assertThat(value).isEqualTo("timed");
            assertThat(eventTypes).containsExactly(
                TimerEvent.Type.START,
                TimerEvent.Type.SUCCESS,
                TimerEvent.Type.START,
                TimerEvent.Type.FAILURE);
            assertThat(meterRegistry.find("resilience4j.timer.calls")
                .tag("name", "reactor-timer")
                .tag("kind", "successful")
                .timer()
                .count()).isEqualTo(1);
            assertThat(meterRegistry.find("resilience4j.timer.calls")
                .tag("name", "reactor-timer")
                .tag("kind", "failed")
                .tag("failure", "IllegalArgumentException")
                .timer()
                .count()).isEqualTo(1);
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void reactorAdapterConvertsResilienceEventPublisherIntoFlux() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("event-circuit-breaker");
        List<CircuitBreakerEvent> events = new ArrayList<>();

        ReactorAdapter.toFlux(circuitBreaker.getEventPublisher())
            .take(1)
            .subscribe(events::add);

        assertThatThrownBy(() -> Mono.<String>error(new IllegalArgumentException("failure"))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .block(BLOCK_TIMEOUT))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getCircuitBreakerName()).isEqualTo("event-circuit-breaker");
    }

    @Test
    void operatorsRejectUnsupportedPublishersWithPublicException() {
        Publisher<String> unsupportedPublisher = subscriber -> { };
        CircuitBreakerOperator<String> operator = CircuitBreakerOperator.of(CircuitBreaker.ofDefaults("unsupported"));

        assertThatThrownBy(() -> operator.apply(unsupportedPublisher))
            .isInstanceOf(IllegalPublisherException.class)
            .hasMessageContaining("not supported");
    }
}
