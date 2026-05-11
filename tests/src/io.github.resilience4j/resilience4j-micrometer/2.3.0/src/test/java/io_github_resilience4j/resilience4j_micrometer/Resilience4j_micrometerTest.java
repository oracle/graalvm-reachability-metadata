/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_micrometer;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.Timer;
import io.github.resilience4j.micrometer.TimerConfig;
import io.github.resilience4j.micrometer.TimerRegistry;
import io.github.resilience4j.micrometer.tagged.RateLimiterMetricNames;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetricsPublisher;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedThreadPoolBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Resilience4j_micrometerTest {

    @Test
    void taggedCircuitBreakerMetricsTrackCallsStatesTagsAndRegistryChanges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowSize(2)
                    .minimumNumberOfCalls(2)
                    .failureRateThreshold(50.0f)
                    .waitDurationInOpenState(Duration.ofSeconds(1))
                    .build();
            CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                    "orders", config, Map.of("scope", "primary"));

            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry).bindTo(meterRegistry);

            assertThat(circuitBreaker.executeSupplier(() -> "ok")).isEqualTo("ok");
            assertThatThrownBy(() -> circuitBreaker.executeRunnable(() -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> circuitBreaker.executeRunnable(() -> { }))
                    .isInstanceOf(CallNotPermittedException.class);

            assertThat(timerCount(meterRegistry, "resilience4j.circuitbreaker.calls", "orders", "successful"))
                    .isEqualTo(1L);
            assertThat(timerCount(meterRegistry, "resilience4j.circuitbreaker.calls", "orders", "failed"))
                    .isEqualTo(1L);
            assertThat(counterCount(
                    meterRegistry, "resilience4j.circuitbreaker.not.permitted.calls", "orders", "not_permitted"))
                    .isEqualTo(1.0d);
            assertThat(gaugeValue(meterRegistry, "resilience4j.circuitbreaker.state", "orders", "state", "open"))
                    .isEqualTo(1.0d);
            assertThat(gaugeValue(
                    meterRegistry, "resilience4j.circuitbreaker.buffered.calls", "orders", "kind", "successful"))
                    .isEqualTo(1.0d);
            assertThat(gaugeValue(
                    meterRegistry, "resilience4j.circuitbreaker.buffered.calls", "orders", "kind", "failed"))
                    .isEqualTo(1.0d);
            assertThat(meterRegistry.get("resilience4j.circuitbreaker.failure.rate")
                    .tag("name", "orders")
                    .tag("scope", "primary")
                    .gauge()
                    .value()).isEqualTo(50.0d);

            CircuitBreaker lateCircuitBreaker = circuitBreakerRegistry.circuitBreaker(
                    "late", config, Map.of("scope", "secondary"));
            assertThat(lateCircuitBreaker.getName()).isEqualTo("late");
            assertThat(meterRegistry.find("resilience4j.circuitbreaker.state").tag("name", "late").gauge()).isNotNull();
            circuitBreakerRegistry.remove("late");
            assertThat(meterRegistry.find("resilience4j.circuitbreaker.state").tag("name", "late").gauge()).isNull();
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void taggedRetryMetricsClassifyCallsByRetryOutcome() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ZERO)
                    .retryExceptions(IllegalStateException.class)
                    .build();
            RetryRegistry retryRegistry = RetryRegistry.of(config);
            Retry retry = retryRegistry.retry("backend", Map.of("client", "inventory"));

            TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);

            assertThat(retry.executeSupplier(() -> "first try")).isEqualTo("first try");

            AtomicInteger attemptsBeforeSuccess = new AtomicInteger();
            assertThat(retry.executeSupplier(() -> {
                if (attemptsBeforeSuccess.incrementAndGet() == 1) {
                    throw new IllegalStateException("retry me");
                }
                return "eventual success";
            })).isEqualTo("eventual success");

            assertThatThrownBy(() -> retry.executeSupplier(() -> {
                throw new IllegalArgumentException("not retryable");
            })).isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> retry.executeSupplier(() -> {
                throw new IllegalStateException("still broken");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(functionCounterCount(
                    meterRegistry, "resilience4j.retry.calls", "backend", "successful_without_retry"))
                    .isEqualTo(1.0d);
            assertThat(functionCounterCount(
                    meterRegistry, "resilience4j.retry.calls", "backend", "successful_with_retry"))
                    .isEqualTo(1.0d);
            assertThat(functionCounterCount(
                    meterRegistry, "resilience4j.retry.calls", "backend", "failed_without_retry"))
                    .isEqualTo(1.0d);
            assertThat(functionCounterCount(meterRegistry, "resilience4j.retry.calls", "backend", "failed_with_retry"))
                    .isEqualTo(1.0d);
            assertThat(meterRegistry.get("resilience4j.retry.calls")
                    .tag("name", "backend")
                    .tag("client", "inventory")
                    .tag("kind", "successful_with_retry")
                    .functionCounter()).isNotNull();
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void taggedRateLimiterAndSemaphoreBulkheadMetricsExposeCurrentCapacity() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                    .limitForPeriod(1)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ZERO)
                    .build();
            RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("api", Map.of("endpoint", "search"));
            TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry).bindTo(meterRegistry);

            assertThat(rateLimiter.acquirePermission()).isTrue();
            assertThat(rateLimiter.acquirePermission()).isFalse();
            assertThat(meterRegistry.get("resilience4j.ratelimiter.available.permissions")
                    .tag("name", "api")
                    .tag("endpoint", "search")
                    .gauge()
                    .value()).isEqualTo(0.0d);
            assertThat(meterRegistry.get("resilience4j.ratelimiter.waiting_threads")
                    .tag("name", "api")
                    .gauge()
                    .value()).isEqualTo(0.0d);

            BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                    .maxConcurrentCalls(1)
                    .maxWaitDuration(Duration.ZERO)
                    .build();
            BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
            Bulkhead bulkhead = bulkheadRegistry.bulkhead("writer", Map.of("resource", "database"));
            TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheadRegistry).bindTo(meterRegistry);

            assertThat(bulkhead.tryAcquirePermission()).isTrue();
            try {
                assertThat(bulkhead.tryAcquirePermission()).isFalse();
                assertThat(meterRegistry.get("resilience4j.bulkhead.available.concurrent.calls")
                        .tag("name", "writer")
                        .tag("resource", "database")
                        .gauge()
                        .value()).isEqualTo(0.0d);
                assertThat(meterRegistry.get("resilience4j.bulkhead.max.allowed.concurrent.calls")
                        .tag("name", "writer")
                        .gauge()
                        .value()).isEqualTo(1.0d);
            } finally {
                bulkhead.onComplete();
            }
            assertThat(meterRegistry.get("resilience4j.bulkhead.available.concurrent.calls")
                    .tag("name", "writer")
                    .gauge()
                    .value()).isEqualTo(1.0d);
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void taggedRateLimiterMetricsPublisherPublishesAndRemovesCustomMeters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            RateLimiterMetricNames metricNames = RateLimiterMetricNames.custom()
                    .availablePermissionsMetricName("custom.ratelimiter.available")
                    .waitingThreadsMetricName("custom.ratelimiter.waiting")
                    .build();
            TaggedRateLimiterMetricsPublisher publisher = new TaggedRateLimiterMetricsPublisher(
                    metricNames, meterRegistry);
            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitForPeriod(1)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ZERO)
                    .build();
            RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(config);
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("published-api", Map.of("endpoint", "reports"));

            publisher.publishMetrics(rateLimiter);

            assertThat(rateLimiter.acquirePermission()).isTrue();
            assertThat(meterRegistry.get("custom.ratelimiter.available")
                    .tag("name", "published-api")
                    .tag("endpoint", "reports")
                    .gauge()
                    .value()).isEqualTo(0.0d);
            assertThat(meterRegistry.find("resilience4j.ratelimiter.available.permissions")
                    .tag("name", "published-api")
                    .gauge()).isNull();

            publisher.removeMetrics(rateLimiter);

            assertThat(meterRegistry.find("custom.ratelimiter.available")
                    .tag("name", "published-api")
                    .gauge()).isNull();
            assertThat(meterRegistry.find("custom.ratelimiter.waiting")
                    .tag("name", "published-api")
                    .gauge()).isNull();
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void taggedThreadPoolBulkheadMetricsExposePoolAndQueueGauges() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ThreadPoolBulkheadConfig config = ThreadPoolBulkheadConfig.custom()
                .coreThreadPoolSize(1)
                .maxThreadPoolSize(1)
                .queueCapacity(2)
                .keepAliveDuration(Duration.ofMillis(100))
                .build();
        try (ThreadPoolBulkheadRegistry bulkheadRegistry = ThreadPoolBulkheadRegistry.of(config)) {
            ThreadPoolBulkhead bulkhead = bulkheadRegistry.bulkhead("workers", Map.of("pool", "reports"));
            TaggedThreadPoolBulkheadMetrics.ofThreadPoolBulkheadRegistry(bulkheadRegistry).bindTo(meterRegistry);

            CompletionStage<String> result = bulkhead.executeSupplier(() -> "done");

            assertThat(result.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo("done");
            assertThat(meterRegistry.get("resilience4j.bulkhead.queue.capacity")
                    .tag("name", "workers")
                    .tag("pool", "reports")
                    .gauge()
                    .value()).isEqualTo(2.0d);
            assertThat(meterRegistry.get("resilience4j.bulkhead.core.thread.pool.size")
                    .tag("name", "workers")
                    .gauge()
                    .value()).isEqualTo(1.0d);
            assertThat(meterRegistry.get("resilience4j.bulkhead.max.thread.pool.size")
                    .tag("name", "workers")
                    .gauge()
                    .value()).isEqualTo(1.0d);
            assertThat(meterRegistry.get("resilience4j.bulkhead.queue.depth")
                    .tag("name", "workers")
                    .gauge()
                    .value()).isEqualTo(0.0d);
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void taggedTimeLimiterMetricsCountSuccessFailureAndTimeout() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            TimeLimiterConfig config = TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofMillis(50))
                    .cancelRunningFuture(true)
                    .build();
            TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(config);
            TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter("external", Map.of("operation", "lookup"));
            TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(timeLimiterRegistry).bindTo(meterRegistry);

            assertThat(timeLimiter.executeCompletionStage(scheduler, () -> CompletableFuture.completedFuture("ok"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThatThrownBy(() -> timeLimiter.executeCompletionStage(scheduler, () -> CompletableFuture.failedFuture(
                            new IllegalStateException("service failed")))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            CompletableFuture<String> neverCompletes = new CompletableFuture<>();
            assertThatThrownBy(() -> timeLimiter.executeCompletionStage(scheduler, () -> neverCompletes)
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS)).hasCauseInstanceOf(TimeoutException.class);

            assertThat(counterCount(meterRegistry, "resilience4j.timelimiter.calls", "external", "successful"))
                    .isEqualTo(1.0d);
            assertThat(counterCount(meterRegistry, "resilience4j.timelimiter.calls", "external", "failed"))
                    .isEqualTo(1.0d);
            assertThat(counterCount(meterRegistry, "resilience4j.timelimiter.calls", "external", "timeout"))
                    .isEqualTo(1.0d);
            assertThat(meterRegistry.get("resilience4j.timelimiter.calls")
                    .tag("name", "external")
                    .tag("operation", "lookup")
                    .tag("kind", "timeout")
                    .counter()).isNotNull();
        } finally {
            scheduler.shutdownNow();
            meterRegistry.close();
        }
    }

    @Test
    void timerRegistryUsesNamedConfigurationsAndGlobalTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            TimerConfig namedConfig = TimerConfig.custom()
                    .metricNames("custom.timer.operations")
                    .onFailureTagResolver(throwable -> "business")
                    .build();
            TimerRegistry timerRegistry = TimerRegistry.of(
                    Map.of("named", namedConfig), List.of(), Map.of("application", "store"), meterRegistry);
            Timer timer = timerRegistry.timer("inventory", "named", Map.of("operation", "reserve"));

            assertThat(timer.getTimerConfig()).isSameAs(namedConfig);
            assertThat(timer.getTags())
                    .containsEntry("application", "store")
                    .containsEntry("operation", "reserve");

            assertThat(timer.executeSupplier(() -> "reserved")).isEqualTo("reserved");
            assertThatThrownBy(() -> timer.executeRunnable(() -> {
                throw new IllegalArgumentException("sold out");
            })).isInstanceOf(IllegalArgumentException.class);

            assertThat(meterRegistry.get("custom.timer.operations")
                    .tag("name", "inventory")
                    .tag("application", "store")
                    .tag("operation", "reserve")
                    .tag("kind", "successful")
                    .timer()
                    .count()).isEqualTo(1L);
            assertThat(meterRegistry.get("custom.timer.operations")
                    .tag("name", "inventory")
                    .tag("application", "store")
                    .tag("operation", "reserve")
                    .tag("kind", "failed")
                    .tag("failure", "business")
                    .timer()
                    .count()).isEqualTo(1L);
            assertThat(meterRegistry.find("resilience4j.timer.calls")
                    .tag("name", "inventory")
                    .timer()).isNull();
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void timerRecordsSynchronousAsynchronousAndRegistryCreatedOperations() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            TimerConfig config = TimerConfig.custom()
                    .metricNames("resilience4j.timer.operations")
                    .onFailureTagResolver(throwable -> throwable.getClass().getSimpleName())
                    .build();
            Timer timer = Timer.of("payments", meterRegistry, config, Map.of("component", "checkout"));
            AtomicInteger starts = new AtomicInteger();
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger failures = new AtomicInteger();
            timer.getEventPublisher()
                    .onStart(event -> starts.incrementAndGet())
                    .onSuccess(event -> successes.incrementAndGet())
                    .onFailure(event -> failures.incrementAndGet());

            assertThat(timer.executeSupplier(() -> "accepted")).isEqualTo("accepted");
            assertThat(timer.executeCompletionStage(() -> CompletableFuture.completedFuture("captured"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS)).isEqualTo("captured");
            assertThatThrownBy(() -> timer.executeRunnable(() -> {
                throw new IllegalStateException("declined");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(starts).hasValue(3);
            assertThat(successes).hasValue(2);
            assertThat(failures).hasValue(1);
            assertThat(timerCount(meterRegistry, "resilience4j.timer.operations", "payments", "successful"))
                    .isEqualTo(2L);
            assertThat(meterRegistry.get("resilience4j.timer.operations")
                    .tag("name", "payments")
                    .tag("component", "checkout")
                    .tag("kind", "failed")
                    .tag("failure", "IllegalStateException")
                    .timer()
                    .count()).isEqualTo(1L);

            TimerRegistry timerRegistry = TimerRegistry.ofDefaults(meterRegistry);
            Timer registryTimer = timerRegistry.timer("registry", Map.of("source", "factory"));
            registryTimer.executeRunnable(() -> { });
            assertThat(meterRegistry.get("resilience4j.timer.calls")
                    .tag("name", "registry")
                    .tag("source", "factory")
                    .tag("kind", "successful")
                    .timer()
                    .count()).isEqualTo(1L);
        } finally {
            meterRegistry.close();
        }
    }

    private static long timerCount(MeterRegistry registry, String metricName, String name, String kind) {
        return registry.get(metricName)
                .tag("name", name)
                .tag("kind", kind)
                .timer()
                .count();
    }

    private static double counterCount(MeterRegistry registry, String metricName, String name, String kind) {
        return registry.get(metricName)
                .tag("name", name)
                .tag("kind", kind)
                .counter()
                .count();
    }

    private static double functionCounterCount(MeterRegistry registry, String metricName, String name, String kind) {
        return registry.get(metricName)
                .tag("name", name)
                .tag("kind", kind)
                .functionCounter()
                .count();
    }

    private static double gaugeValue(
            MeterRegistry registry,
            String metricName,
            String name,
            String discriminatorTag,
            String discriminatorValue) {
        return registry.get(metricName)
                .tag("name", name)
                .tag(discriminatorTag, discriminatorValue)
                .gauge()
                .value();
    }
}
