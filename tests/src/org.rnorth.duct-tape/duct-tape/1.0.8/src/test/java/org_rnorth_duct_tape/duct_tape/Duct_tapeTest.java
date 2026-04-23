/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rnorth_duct_tape.duct_tape;

import org.junit.jupiter.api.Test;
import org.rnorth.ducttape.RetryCountExceededException;
import org.rnorth.ducttape.TimeoutException;
import org.rnorth.ducttape.circuitbreakers.Breaker;
import org.rnorth.ducttape.circuitbreakers.BreakerBuilder;
import org.rnorth.ducttape.circuitbreakers.State;
import org.rnorth.ducttape.circuitbreakers.StateStore;
import org.rnorth.ducttape.inconsistents.InconsistentResultsException;
import org.rnorth.ducttape.inconsistents.Inconsistents;
import org.rnorth.ducttape.inconsistents.ResultsNeverConsistentException;
import org.rnorth.ducttape.ratelimits.RateLimiter;
import org.rnorth.ducttape.ratelimits.RateLimiterBuilder;
import org.rnorth.ducttape.timeouts.Timeouts;
import org.rnorth.ducttape.unreliables.Unreliables;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Duct_tapeTest {

    @Test
    void timeoutsExecuteWorkAndWrapFailures() {
        AtomicBoolean executed = new AtomicBoolean();

        Timeouts.doWithTimeout(1, TimeUnit.SECONDS, () -> executed.set(true));
        String value = Timeouts.getWithTimeout(1, TimeUnit.SECONDS, () -> "value");

        RuntimeException executionFailure = assertThrows(RuntimeException.class,
                () -> Timeouts.getWithTimeout(1, TimeUnit.SECONDS, () -> {
                    throw new IllegalStateException("boom");
                }));

        assertThat(executed.get()).isTrue();
        assertThat(value).isEqualTo("value");
        assertThat(executionFailure).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(executionFailure.getCause()).hasMessage("boom");
    }

    @Test
    void timeoutsRejectInvalidTimeoutsAndInterruptLongRunningCalls() {
        assertThatThrownBy(() -> Timeouts.doWithTimeout(0, TimeUnit.MILLISECONDS, () -> {
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout must be greater than zero");

        TimeoutException timeoutException = assertThrows(TimeoutException.class,
                () -> Timeouts.getWithTimeout(50, TimeUnit.MILLISECONDS, () -> {
                    Thread.sleep(200);
                    return "late";
                }));

        assertThat(timeoutException).hasCauseInstanceOf(Exception.class);
    }

    @Test
    void unreliablesRetryUntilSuccessAndTrueAcrossTimeoutBasedAndCountBasedApis() {
        AtomicInteger successAttempts = new AtomicInteger();
        String successValue = Unreliables.retryUntilSuccess(1, TimeUnit.SECONDS, () -> {
            if (successAttempts.getAndIncrement() < 2) {
                throw new IllegalStateException("not yet");
            }
            return "ready";
        });

        AtomicInteger trueAttempts = new AtomicInteger();
        Unreliables.retryUntilTrue(1, TimeUnit.SECONDS, () -> trueAttempts.incrementAndGet() >= 3);

        AtomicInteger countedSuccessAttempts = new AtomicInteger();
        Integer countedValue = Unreliables.retryUntilSuccess(3, () -> {
            if (countedSuccessAttempts.getAndIncrement() == 0) {
                throw new IllegalStateException("retry once");
            }
            return 42;
        });

        AtomicInteger countedTrueAttempts = new AtomicInteger();
        Unreliables.retryUntilTrue(3, () -> countedTrueAttempts.incrementAndGet() >= 2);

        assertThat(successValue).isEqualTo("ready");
        assertThat(successAttempts.get()).isEqualTo(3);
        assertThat(trueAttempts.get()).isGreaterThanOrEqualTo(3);
        assertThat(countedValue).isEqualTo(42);
        assertThat(countedSuccessAttempts.get()).isEqualTo(2);
        assertThat(countedTrueAttempts.get()).isEqualTo(2);
    }

    @Test
    void unreliablesSurfaceTheLastFailureWhenRetriesAreExhausted() {
        TimeoutException timeoutFailure = assertThrows(TimeoutException.class,
                () -> Unreliables.retryUntilSuccess(100, TimeUnit.MILLISECONDS, () -> {
                    throw new IllegalStateException("still failing");
                }));

        TimeoutException timeoutFromBoolean = assertThrows(TimeoutException.class,
                () -> Unreliables.retryUntilTrue(50, TimeUnit.MILLISECONDS, () -> {
                    Thread.sleep(100);
                    return true;
                }));

        RetryCountExceededException countFailure = assertThrows(RetryCountExceededException.class,
                () -> Unreliables.retryUntilSuccess(2, () -> {
                    throw new IllegalStateException("always failing");
                }));

        RetryCountExceededException falseCountFailure = assertThrows(RetryCountExceededException.class,
                () -> Unreliables.retryUntilTrue(2, () -> false));

        assertThat(timeoutFailure).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(timeoutFailure.getCause()).hasMessage("still failing");
        assertThat(timeoutFromBoolean).hasCauseInstanceOf(Exception.class);
        assertThat(countFailure).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(countFailure.getCause()).hasMessage("always failing");
        assertThat(falseCountFailure.getCause()).isInstanceOf(RuntimeException.class);
        assertThat(falseCountFailure.getCause()).hasMessage("Not ready yet");
    }

    @Test
    void inconsistentsReturnOnceAValueStaysStableLongEnough() {
        AtomicInteger attempt = new AtomicInteger();

        Integer result = Inconsistents.retryUntilConsistent(30, 500, TimeUnit.MILLISECONDS, () -> {
            Thread.sleep(20);
            return attempt.incrementAndGet() < 3 ? 1 : 2;
        });

        assertThat(result).isEqualTo(2);
        assertThat(attempt.get()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void inconsistentsReportWhetherValuesWereNeverConsistentOrOnlyBrieflyConsistent() {
        AtomicInteger changingAttempt = new AtomicInteger();
        TimeoutException neverConsistent = assertThrows(TimeoutException.class,
                () -> Inconsistents.retryUntilConsistent(30, 150, TimeUnit.MILLISECONDS, () -> {
                    Thread.sleep(15);
                    return changingAttempt.incrementAndGet();
                }));

        AtomicInteger patternedAttempt = new AtomicInteger();
        TimeoutException brieflyConsistent = assertThrows(TimeoutException.class,
                () -> Inconsistents.retryUntilConsistent(100, 180, TimeUnit.MILLISECONDS, () -> {
                    Thread.sleep(5);
                    return patternedAttempt.getAndIncrement() % 6 < 4 ? "alpha" : "beta";
                }));

        assertThat(neverConsistent.getCause()).isInstanceOf(ResultsNeverConsistentException.class);
        assertThat(((ResultsNeverConsistentException) neverConsistent.getCause()).getTimeSinceStart()).isPositive();

        assertThat(brieflyConsistent.getCause()).isInstanceOf(InconsistentResultsException.class);
        InconsistentResultsException inconsistentResultsException = (InconsistentResultsException) brieflyConsistent.getCause();
        assertThat(inconsistentResultsException.getMostConsistentValue()).isEqualTo("alpha");
        assertThat(inconsistentResultsException.getMostConsistentTime()).isGreaterThan(0L);
    }

    @Test
    void breakerTripsInvokesHandlersAndAutoResets() throws InterruptedException {
        Breaker breaker = BreakerBuilder.newBuilder()
                .autoResetAfter(100, TimeUnit.MILLISECONDS)
                .build();
        List<String> calls = new ArrayList<>();

        breaker.tryDo(() -> {
            calls.add("primary");
            throw new IllegalStateException("boom");
        }, () -> calls.add("failed"), () -> calls.add("broken"));
        breaker.tryDo(() -> calls.add("should-not-run"), () -> calls.add("broken-only"));

        Thread.sleep(200);

        breaker.tryDo(() -> calls.add("after-reset"), () -> calls.add("broken-after-reset"));

        assertThat(calls).containsExactly("primary", "failed", "broken", "broken-only", "after-reset");
        assertThat(breaker.getState()).isEqualTo(State.OK);
    }

    @Test
    void breakerTryGetUsesFallbacksOptionalResultsAndSharedExternalState() {
        Breaker breaker = BreakerBuilder.newBuilder().build();
        List<String> events = new ArrayList<>();

        String recovered = breaker.tryGet(() -> {
            events.add("primary");
            throw new IllegalStateException("fail");
        }, () -> events.add("failed"), () -> {
            events.add("fallback");
            return "fallback";
        });

        AtomicBoolean brokenPrimaryCalled = new AtomicBoolean();
        String brokenResult = breaker.tryGet(() -> {
            brokenPrimaryCalled.set(true);
            return "primary";
        }, () -> "broken");
        AtomicBoolean optionalPrimaryCalled = new AtomicBoolean();

        assertThat(recovered).isEqualTo("fallback");
        assertThat(brokenResult).isEqualTo("broken");
        assertThat(breaker.tryGet(() -> {
            optionalPrimaryCalled.set(true);
            return "ignored";
        })).isEmpty();
        assertThat(events).containsExactly("primary", "failed", "fallback");
        assertThat(brokenPrimaryCalled.get()).isFalse();
        assertThat(optionalPrimaryCalled.get()).isFalse();

        ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
        Breaker firstSharedBreaker = BreakerBuilder.newBuilder().storeStateIn(state, "shared").build();
        Breaker secondSharedBreaker = BreakerBuilder.newBuilder().storeStateIn(state, "shared").build();
        Breaker isolatedBreaker = BreakerBuilder.newBuilder().storeStateIn(state, "isolated").build();

        assertThat(state).isEmpty();
        firstSharedBreaker.tryDo(() -> {
            throw new IllegalStateException("trip shared state");
        });

        AtomicBoolean sharedPrimaryCalled = new AtomicBoolean();
        assertThat(secondSharedBreaker.tryGet(() -> {
            sharedPrimaryCalled.set(true);
            return "primary";
        }, () -> "shared-fallback")).isEqualTo("shared-fallback");
        assertThat(sharedPrimaryCalled.get()).isFalse();
        assertThat(state).hasSize(2);
        assertThat(isolatedBreaker.tryGet(() -> "primary")).contains("primary");
    }

    @Test
    void rateLimiterValidatesItsBuilderAndSpacesOutCalls() throws Exception {
        assertThatThrownBy(() -> RateLimiterBuilder.newBuilder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A rate must be set");
        assertThatThrownBy(() -> RateLimiterBuilder.newBuilder().withRate(5, TimeUnit.SECONDS).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A rate limit strategy must be set");

        RateLimiter rateLimiter = RateLimiterBuilder.newBuilder()
                .withRate(5, TimeUnit.SECONDS)
                .withConstantThroughput()
                .build();

        List<Long> invocationTimes = new ArrayList<>();
        rateLimiter.doWhenReady(() -> invocationTimes.add(System.nanoTime()));
        rateLimiter.doWhenReady(() -> invocationTimes.add(System.nanoTime()));
        Integer result = rateLimiter.getWhenReady(() -> 7);

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(invocationTimes.get(1) - invocationTimes.get(0));
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(150L);
        assertThat(result).isEqualTo(7);
    }

    @Test
    void rateLimiterKeepsItsSpacingAfterAFailedInvocation() {
        RateLimiter rateLimiter = RateLimiterBuilder.newBuilder()
                .withRate(5, TimeUnit.SECONDS)
                .withConstantThroughput()
                .build();

        rateLimiter.doWhenReady(() -> {
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> rateLimiter.getWhenReady(() -> {
                    throw new IllegalStateException("boom");
                }));

        AtomicBoolean recoveryExecuted = new AtomicBoolean();
        long recoveryStart = System.nanoTime();
        rateLimiter.doWhenReady(() -> recoveryExecuted.set(true));
        long recoveryDelayMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - recoveryStart);

        assertThat(failure).hasMessage("boom");
        assertThat(recoveryExecuted.get()).isTrue();
        assertThat(recoveryDelayMillis).isGreaterThanOrEqualTo(150L);
    }

    @Test
    void breakerCanPersistAndReadStateThroughACustomStateStore() {
        RecordingStateStore stateStore = new RecordingStateStore();
        Breaker breaker = BreakerBuilder.newBuilder().storeStateIn(stateStore).build();
        AtomicBoolean failedHandlerCalled = new AtomicBoolean();

        String fallback = breaker.tryGet(() -> {
            throw new IllegalStateException("boom");
        }, () -> failedHandlerCalled.set(true), () -> "fallback");

        assertThat(fallback).isEqualTo("fallback");
        assertThat(failedHandlerCalled.get()).isTrue();
        assertThat(stateStore.getState()).isEqualTo(State.BROKEN);
        assertThat(stateStore.getLastFailure()).isPositive();

        AtomicBoolean blockedPrimaryCalled = new AtomicBoolean();
        assertThat(breaker.tryGet(() -> {
            blockedPrimaryCalled.set(true);
            return "primary";
        }, () -> "broken"))
                .isEqualTo("broken");
        assertThat(blockedPrimaryCalled.get()).isFalse();

        stateStore.setState(State.OK);

        assertThat(breaker.tryGet(() -> "recovered")).contains("recovered");
        assertThat(breaker.getState()).isEqualTo(State.OK);
    }

    private static final class RecordingStateStore implements StateStore {

        private final AtomicReference<State> state = new AtomicReference<>(State.OK);
        private final AtomicLong lastFailure = new AtomicLong();

        @Override
        public State getState() {
            return state.get();
        }

        @Override
        public void setState(State newState) {
            state.set(newState);
        }

        @Override
        public long getLastFailure() {
            return lastFailure.get();
        }

        @Override
        public void setLastFailure(long newLastFailure) {
            lastFailure.set(newLastFailure);
        }
    }
}
