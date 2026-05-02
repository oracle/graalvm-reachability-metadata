/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe.failsafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.failsafe.Bulkhead;
import dev.failsafe.BulkheadFullException;
import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RateLimitExceededException;
import dev.failsafe.RateLimiter;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import dev.failsafe.TimeoutExceededException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FailsafeTest {
    @Test
    void retryPolicyRetriesHandledResultsAndPublishesAttemptEvents() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger failedAttempts = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<String> lastFailedResult = new AtomicReference<>();

        RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                .handleResult("retry")
                .withMaxRetries(2)
                .onFailedAttempt(event -> {
                    failedAttempts.incrementAndGet();
                    lastFailedResult.set(event.getLastResult());
                    assertThat(event.getAttemptCount()).isBetween(1, 2);
                })
                .onRetry(event -> retries.incrementAndGet())
                .onSuccess(event -> {
                    completions.incrementAndGet();
                    assertThat(event.getResult()).isEqualTo("ok");
                    assertThat(event.getAttemptCount()).isEqualTo(3);
                })
                .build();

        String result = Failsafe.with(retryPolicy).get(() -> attempts.incrementAndGet() < 3 ? "retry" : "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
        assertThat(failedAttempts).hasValue(2);
        assertThat(retries).hasValue(2);
        assertThat(completions).hasValue(1);
        assertThat(lastFailedResult).hasValue("retry");
    }

    @Test
    void fallbackComputesReplacementFromFailureEvent() {
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        Fallback<String> fallback = Fallback.of(event -> {
            observedFailure.set(event.getLastException());
            return "fallback after " + event.getAttemptCount() + " attempt";
        });

        String result = Failsafe.with(fallback).get(() -> {
            throw new IllegalArgumentException("primary failed");
        });

        assertThat(result).isEqualTo("fallback after 1 attempt");
        assertThat(observedFailure.get())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("primary failed");
    }

    @Test
    void circuitBreakerOpensAfterThresholdAndRejectsProtectedExecutions() {
        AtomicInteger openEvents = new AtomicInteger();
        AtomicInteger guardedCalls = new AtomicInteger();
        CircuitBreaker<String> circuitBreaker = CircuitBreaker.<String>builder()
                .handle(IllegalStateException.class)
                .withFailureThreshold(2)
                .withSuccessThreshold(1)
                .withDelay(Duration.ofSeconds(30))
                .onOpen(event -> openEvents.incrementAndGet())
                .build();

        assertThatThrownBy(() -> Failsafe.with(circuitBreaker).get(() -> failWithIllegalState(guardedCalls)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom 1");
        assertThat(circuitBreaker.isClosed()).isTrue();

        assertThatThrownBy(() -> Failsafe.with(circuitBreaker).get(() -> failWithIllegalState(guardedCalls)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom 2");
        assertThat(circuitBreaker.isOpen()).isTrue();
        assertThat(openEvents).hasValue(1);

        assertThatThrownBy(() -> Failsafe.with(circuitBreaker).get(() -> {
            guardedCalls.incrementAndGet();
            return "should not run";
        }))
                .isInstanceOf(CircuitBreakerOpenException.class);
        assertThat(guardedCalls).hasValue(2);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(2);
        assertThat(circuitBreaker.getRemainingDelay()).isPositive();

        circuitBreaker.close();
        assertThat(Failsafe.with(circuitBreaker).get(() -> "recovered")).isEqualTo("recovered");
        assertThat(circuitBreaker.isClosed()).isTrue();
        assertThat(circuitBreaker.getSuccessCount()).isEqualTo(1);
    }

    @Test
    void bulkheadRejectsExecutionWhenNoPermitIsAvailable() throws Exception {
        Bulkhead<String> bulkhead = Bulkhead.<String>builder(1)
                .withMaxWaitTime(Duration.ZERO)
                .build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch enteredBulkhead = new CountDownLatch(1);
        CountDownLatch releaseBulkhead = new CountDownLatch(1);

        try {
            CompletableFuture<String> firstExecution = Failsafe.with(bulkhead).with(executor).getAsync(() -> {
                enteredBulkhead.countDown();
                if (!releaseBulkhead.await(2, TimeUnit.SECONDS)) {
                    throw new TimeoutException("bulkhead holder was not released");
                }
                return "first";
            });

            assertThat(enteredBulkhead.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> Failsafe.with(bulkhead).get(() -> "second"))
                    .isInstanceOf(BulkheadFullException.class);

            releaseBulkhead.countDown();
            assertThat(firstExecution.get(2, TimeUnit.SECONDS)).isEqualTo("first");
        } finally {
            releaseBulkhead.countDown();
            shutdown(executor);
        }
    }

    @Test
    void rateLimiterFailsFastWhenConfiguredWaitTimeIsExhausted() {
        RateLimiter<String> rateLimiter = RateLimiter.<String>burstyBuilder(1, Duration.ofHours(1))
                .withMaxWaitTime(Duration.ZERO)
                .build();

        assertThat(Failsafe.with(rateLimiter).get(() -> "permitted")).isEqualTo("permitted");
        assertThatThrownBy(() -> Failsafe.with(rateLimiter).get(() -> "limited"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(rateLimiter.isBursty()).isTrue();
        assertThat(rateLimiter.isSmooth()).isFalse();
    }

    @Test
    void timeoutInterruptsLongRunningSynchronousExecution() {
        AtomicBoolean interrupted = new AtomicBoolean();
        Timeout<Void> timeout = Timeout.<Void>builder(Duration.ofMillis(50))
                .withInterrupt()
                .build();

        try {
            assertThatThrownBy(() -> Failsafe.with(timeout).run(() -> {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    throw e;
                }
            })).isInstanceOf(TimeoutExceededException.class);
        } finally {
            Thread.interrupted();
        }

        assertThat(interrupted).isTrue();
    }

    @Test
    void asynchronousStageIsRetriedUntilCompletionResultIsAccepted() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                .handleResult("retry")
                .withMaxRetries(1)
                .build();

        CompletableFuture<String> future = Failsafe.with(retryPolicy).getStageAsync(() ->
                CompletableFuture.completedFuture(attempts.incrementAndGet() == 1 ? "retry" : "async-ok"));

        assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo("async-ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void asynchronousExecutionCanBeCompletedManually() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> future = Failsafe.<String>none().with(executor).getAsyncExecution(execution ->
                    executor.execute(() -> execution.recordResult("manual-result")));

            assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo("manual-result");
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void newCallReusesPolicyStateAcrossExecutions() {
        RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                .handleResult("retry")
                .withMaxRetries(1)
                .build();
        AtomicInteger attempts = new AtomicInteger();

        List<String> results = List.of(
                Failsafe.with(retryPolicy)
                        .newCall(context -> attempts.incrementAndGet() == 1 ? "retry" : "ok")
                        .execute(),
                Failsafe.<String>none().newCall(context -> "plain").execute());

        assertThat(results).containsExactly("ok", "plain");
        assertThat(attempts).hasValue(2);
    }

    private static String failWithIllegalState(AtomicInteger guardedCalls) {
        int attempt = guardedCalls.incrementAndGet();
        throw new IllegalStateException("boom " + attempt);
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }
}
