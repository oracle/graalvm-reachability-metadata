/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micronaut.context.ApplicationContext;
import io.micronaut.retry.CircuitBreakerPolicy;
import io.micronaut.retry.RetryOperations;
import io.micronaut.retry.RetryOperationsFactory;
import io.micronaut.retry.RetryPolicy;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.annotation.DefaultRetryPredicate;
import io.micronaut.retry.annotation.RetryPredicate;
import io.micronaut.retry.annotation.Retryable;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Micronaut_retryTest {
    @Test
    void evaluatesDefaultRetryPredicateIncludeAndExcludeRules() {
        DefaultRetryPredicate predicate = new DefaultRetryPredicate(
                List.of(RuntimeException.class), List.of(IllegalArgumentException.class));

        assertThat(predicate.test(new IllegalStateException("retryable"))).isTrue();
        assertThat(predicate.test(new IllegalArgumentException("excluded"))).isFalse();
        assertThat(predicate.test(new Exception("not included"))).isFalse();
    }

    @Test
    void buildsRetryPolicyWithTypedConfiguration() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(4)
                .delay(Duration.ofMillis(25))
                .maxDelay(Duration.ofSeconds(2))
                .multiplier(2.0)
                .jitter(0.25)
                .capturedException(IllegalStateException.class)
                .includes(RuntimeException.class)
                .excludes(IllegalArgumentException.class)
                .build();

        assertThat(policy.maxAttempts()).isEqualTo(4);
        assertThat(policy.delay()).isEqualTo(Duration.ofMillis(25));
        assertThat(policy.getMaxDelay()).contains(Duration.ofSeconds(2));
        assertThat(policy.multiplier()).isEqualTo(2.0);
        assertThat(policy.jitter()).isEqualTo(0.25);
        assertThat(policy.capturedException()).isEqualTo(IllegalStateException.class);
        assertThat(policy.includes()).containsExactly(RuntimeException.class);
        assertThat(policy.excludes()).containsExactly(IllegalArgumentException.class);
        assertThat(policy.predicate().test(new IllegalStateException())).isTrue();
        assertThat(policy.predicate().test(new IllegalArgumentException())).isFalse();
    }

    @Test
    void buildsCircuitBreakerPolicyFromRetryConfiguration() {
        CircuitBreakerPolicy policy = CircuitBreakerPolicy.builder()
                .maxAttempts(5)
                .delay(Duration.ofMillis(10))
                .maxDelay(Duration.ofSeconds(1))
                .multiplier(1.5)
                .jitter(0.1)
                .resetTimeout(Duration.ofSeconds(30))
                .throwWrappedException(true)
                .includes(IllegalStateException.class)
                .build();

        assertThat(policy.getMaxAttempts()).isEqualTo(5);
        assertThat(policy.getDelay()).isEqualTo(Duration.ofMillis(10));
        assertThat(policy.getMaxDelay()).contains(Duration.ofSeconds(1));
        assertThat(policy.getMultiplier()).isEqualTo(1.5);
        assertThat(policy.getJitter()).isEqualTo(0.1);
        assertThat(policy.getResetTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.isThrowWrappedException()).isTrue();
        assertThat(policy.getIncludes()).containsExactly(IllegalStateException.class);
        assertThat(policy.asRetryPolicy().maxAttempts()).isEqualTo(5);
    }

    @Test
    @Timeout(55)
    void retriesSynchronousOperationsUntilTheySucceed() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            RetryOperations operations = RetryOperationsFactory.create(scheduler)
                    .createRetryOperations(zeroDelayPolicy(3));
            AtomicInteger attempts = new AtomicInteger();

            String result = operations.execute(() -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("transient failure");
                }
                return "success";
            });

            assertThat(result).isEqualTo("success");
            assertThat(attempts).hasValue(3);
        } finally {
            stop(scheduler);
        }
    }

    @Test
    @Timeout(55)
    void retriesAnnotatedMethodsUntilTheySucceed() {
        TransientFailurePredicate.evaluations.set(0);
        try (ApplicationContext context = ApplicationContext.run()) {
            RetryableService service = context.getBean(RetryableService.class);

            assertThat(service.call()).isEqualTo("annotated success");
            assertThat(service.attempts).hasValue(3);
            assertThat(TransientFailurePredicate.evaluations).hasValue(2);
        }
    }

    @Test
    @Timeout(55)
    void opensCircuitAfterRetryAttemptsAreExhausted() {
        try (ApplicationContext context = ApplicationContext.run()) {
            CircuitBreakerService service = context.getBean(CircuitBreakerService.class);

            assertThatThrownBy(service::call)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("service unavailable");
            assertThat(service.attempts).hasValue(3);

            assertThatThrownBy(service::call)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("service unavailable");
            assertThat(service.attempts).hasValue(3);
        }
    }

    @Test
    @Timeout(55)
    void doesNotRetryAnExceptionOutsideTheCapturedType() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .delay(Duration.ZERO)
                    .capturedException(IllegalStateException.class)
                    .build();
            RetryOperations operations = RetryOperationsFactory.create(scheduler).createRetryOperations(policy);
            AtomicInteger attempts = new AtomicInteger();

            assertThatThrownBy(() -> operations.execute(() -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("not captured");
            }))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("not captured");
            assertThat(attempts).hasValue(1);
        } finally {
            stop(scheduler);
        }
    }

    @Test
    @Timeout(55)
    void retriesCompletionStageOperations() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            RetryOperations operations = RetryOperationsFactory.create(scheduler)
                    .createRetryOperations(zeroDelayPolicy(3));
            AtomicInteger attempts = new AtomicInteger();

            CompletionStage<String> result = operations.executeCompletionStage(() -> {
                if (attempts.incrementAndGet() < 3) {
                    return CompletableFuture.failedFuture(new IllegalStateException("transient failure"));
                }
                return CompletableFuture.completedFuture("async success");
            });

            assertThat(result.toCompletableFuture().get(10, TimeUnit.SECONDS)).isEqualTo("async success");
            assertThat(attempts).hasValue(3);
        } finally {
            stop(scheduler);
        }
    }

    @Test
    @Timeout(55)
    void retriesPublisherOperations() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            RetryOperations operations = RetryOperationsFactory.create(scheduler)
                    .createRetryOperations(zeroDelayPolicy(3));
            AtomicInteger attempts = new AtomicInteger();
            CompletableFuture<String> result = new CompletableFuture<>();

            Publisher<String> publisher = operations.executePublisher(() -> new RetryingPublisher(attempts));
            publisher.subscribe(new Subscriber<>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(String value) {
                    result.complete(value);
                }

                @Override
                public void onError(Throwable throwable) {
                    result.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    if (!result.isDone()) {
                        result.completeExceptionally(new IllegalStateException("publisher completed without a value"));
                    }
                }
            });

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("reactive success");
            assertThat(attempts).hasValue(3);
        } finally {
            stop(scheduler);
        }
    }

    @Singleton
    public static class RetryableService {
        private final AtomicInteger attempts = new AtomicInteger();

        @Retryable(attempts = "3", delay = "0ms", predicate = TransientFailurePredicate.class)
        public String call() {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient failure");
            }
            return "annotated success";
        }
    }

    public static final class TransientFailurePredicate implements RetryPredicate {
        private static final AtomicInteger evaluations = new AtomicInteger();

        @Override
        public boolean test(Throwable throwable) {
            evaluations.incrementAndGet();
            return throwable instanceof IllegalStateException;
        }
    }

    @Singleton
    public static class CircuitBreakerService {
        private final AtomicInteger attempts = new AtomicInteger();

        @CircuitBreaker(attempts = "2", delay = "0ms", reset = "10s")
        public String call() {
            attempts.incrementAndGet();
            throw new IllegalStateException("service unavailable");
        }
    }

    private static RetryPolicy zeroDelayPolicy(int maxAttempts) {
        return RetryPolicy.builder()
                .maxAttempts(maxAttempts)
                .delay(Duration.ZERO)
                .capturedException(RuntimeException.class)
                .build();
    }

    private static void stop(ScheduledExecutorService scheduler) throws InterruptedException {
        scheduler.shutdownNow();
        assertThat(scheduler.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private static final class RetryingPublisher implements Publisher<String> {
        private final AtomicInteger attempts;

        private RetryingPublisher(AtomicInteger attempts) {
            this.attempts = attempts;
        }

        @Override
        public void subscribe(Subscriber<? super String> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                private boolean cancelled;

                @Override
                public void request(long count) {
                    if (cancelled || count <= 0) {
                        return;
                    }
                    cancelled = true;
                    if (attempts.incrementAndGet() < 3) {
                        subscriber.onError(new IllegalStateException("transient failure"));
                    } else {
                        subscriber.onNext("reactive success");
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }
    }
}
