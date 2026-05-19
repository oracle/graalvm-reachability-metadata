/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_spring_boot3;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.CustomizerWithName;
import io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigCustomizer;
import io.github.resilience4j.common.bulkhead.configuration.CommonBulkheadConfigurationProperties;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CommonCircuitBreakerConfigurationProperties;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerUpdateStateResponse;
import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.UpdateState;
import io.github.resilience4j.common.micrometer.configuration.CommonTimerConfigurationProperties;
import io.github.resilience4j.common.micrometer.configuration.TimerConfigCustomizer;
import io.github.resilience4j.common.ratelimiter.configuration.CommonRateLimiterConfigurationProperties;
import io.github.resilience4j.common.ratelimiter.configuration.RateLimiterConfigCustomizer;
import io.github.resilience4j.common.retry.configuration.CommonRetryConfigurationProperties;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.common.timelimiter.configuration.CommonTimeLimiterConfigurationProperties;
import io.github.resilience4j.common.timelimiter.configuration.TimeLimiterConfigCustomizer;
import io.github.resilience4j.consumer.CircularEventConsumer;
import io.github.resilience4j.consumer.DefaultEventConsumerRegistry;
import io.github.resilience4j.consumer.EventConsumerRegistry;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.micrometer.Timer;
import io.github.resilience4j.micrometer.TimerConfig;
import io.github.resilience4j.micrometer.TimerRegistry;
import io.github.resilience4j.micrometer.event.TimerEvent;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.event.RateLimiterEvent;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryEvent;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadProperties;
import io.github.resilience4j.springboot3.bulkhead.monitoring.endpoint.BulkheadEndpoint;
import io.github.resilience4j.springboot3.bulkhead.monitoring.endpoint.BulkheadEventsEndpoint;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties;
import io.github.resilience4j.springboot3.circuitbreaker.monitoring.endpoint.CircuitBreakerEndpoint;
import io.github.resilience4j.springboot3.circuitbreaker.monitoring.endpoint.CircuitBreakerEventsEndpoint;
import io.github.resilience4j.springboot3.circuitbreaker.monitoring.health.CircuitBreakersHealthIndicator;
import io.github.resilience4j.springboot3.micrometer.autoconfigure.TimerProperties;
import io.github.resilience4j.springboot3.micrometer.monitoring.endpoint.TimerEndpoint;
import io.github.resilience4j.springboot3.micrometer.monitoring.endpoint.TimerEventsEndpoint;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterProperties;
import io.github.resilience4j.springboot3.ratelimiter.monitoring.endpoint.RateLimiterEndpoint;
import io.github.resilience4j.springboot3.ratelimiter.monitoring.endpoint.RateLimiterEventsEndpoint;
import io.github.resilience4j.springboot3.ratelimiter.monitoring.health.RateLimitersHealthIndicator;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryProperties;
import io.github.resilience4j.springboot3.retry.monitoring.endpoint.RetryEndpoint;
import io.github.resilience4j.springboot3.retry.monitoring.endpoint.RetryEventsEndpoint;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterProperties;
import io.github.resilience4j.springboot3.timelimiter.monitoring.endpoint.TimeLimiterEndpoint;
import io.github.resilience4j.springboot3.timelimiter.monitoring.endpoint.TimeLimiterEventsEndpoint;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.timelimiter.event.TimeLimiterEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Resilience4j_spring_boot3Test {

    @Test
    void actuatorEndpointsExposeRegistryStateAndApplyCircuitBreakerUpdates() {
        CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.ofDefaults();
        circuitBreakers.circuitBreaker("orders");
        circuitBreakers.circuitBreaker("billing");

        CircuitBreakerEndpoint circuitBreakerEndpoint = new CircuitBreakerEndpoint(circuitBreakers);
        assertThat(circuitBreakerEndpoint.getAllCircuitBreakers().getCircuitBreakers())
                .containsOnlyKeys("billing", "orders");

        CircuitBreakerUpdateStateResponse forcedOpen = circuitBreakerEndpoint
                .updateCircuitBreakerState("orders", UpdateState.FORCE_OPEN);
        assertThat(forcedOpen.getCircuitBreakerName()).isEqualTo("orders");
        assertThat(forcedOpen.getCurrentState()).isEqualTo(CircuitBreaker.State.FORCED_OPEN.toString());
        assertThat(circuitBreakers.circuitBreaker("orders").getState()).isEqualTo(CircuitBreaker.State.FORCED_OPEN);

        CircuitBreakerUpdateStateResponse closed = circuitBreakerEndpoint
                .updateCircuitBreakerState("orders", UpdateState.CLOSE);
        assertThat(closed.getCurrentState()).isEqualTo(CircuitBreaker.State.CLOSED.toString());

        RetryRegistry retries = RetryRegistry.ofDefaults();
        retries.retry("remote");
        retries.retry("database");
        assertThat(new RetryEndpoint(retries).getAllRetries().getRetries()).containsExactly("database", "remote");

        RateLimiterRegistry rateLimiters = RateLimiterRegistry.ofDefaults();
        rateLimiters.rateLimiter("api");
        rateLimiters.rateLimiter("batch");
        assertThat(new RateLimiterEndpoint(rateLimiters).getAllRateLimiters().getRateLimiters())
                .containsExactly("api", "batch");

        BulkheadRegistry bulkheads = BulkheadRegistry.ofDefaults();
        bulkheads.bulkhead("worker");
        ThreadPoolBulkheadRegistry threadPoolBulkheads = ThreadPoolBulkheadRegistry.ofDefaults();
        threadPoolBulkheads.bulkhead("executor");
        assertThat(new BulkheadEndpoint(bulkheads, threadPoolBulkheads).getAllBulkheads().getBulkheads())
                .containsExactly("executor", "worker");

        TimeLimiterRegistry timeLimiters = TimeLimiterRegistry.ofDefaults();
        timeLimiters.timeLimiter("fast");
        timeLimiters.timeLimiter("slow");
        assertThat(new TimeLimiterEndpoint(timeLimiters).getAllTimeLimiters().getTimeLimiters())
                .containsExactly("fast", "slow");

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TimerRegistry timers = TimerRegistry.ofDefaults(meterRegistry);
        timers.timer("outbound");
        timers.timer("repository");
        assertThat(new TimerEndpoint(timers).getAllRetries().getTimers())
                .containsExactly("outbound", "repository");
    }

    @Test
    void eventEndpointsExposeBufferedEventsWithNameAndTypeFilters() throws Throwable {
        CircuitBreaker circuitBreaker = circuitBreakerWithEvents();
        assertThrows(IllegalArgumentException.class,
                CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> {
                    throw new IllegalArgumentException("boom");
                })::get);
        assertThrows(CallNotPermittedException.class,
                CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> "blocked")::get);

        EventConsumerRegistry<CircuitBreakerEvent> circuitBreakerEvents = new DefaultEventConsumerRegistry<>();
        CircularEventConsumer<CircuitBreakerEvent> circuitBreakerConsumer = registerConsumer(circuitBreakerEvents,
                "orders");
        circuitBreaker.getEventPublisher().onEvent(circuitBreakerConsumer);
        circuitBreaker.reset();
        circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, new IllegalStateException("recorded"));
        CircuitBreakerEventsEndpoint circuitBreakerEventsEndpoint = new CircuitBreakerEventsEndpoint(
                circuitBreakerEvents);
        assertThat(circuitBreakerEventsEndpoint.getAllCircuitBreakerEvents().getCircuitBreakerEvents()).isNotEmpty();
        assertThat(circuitBreakerEventsEndpoint.getEventsFilteredByCircuitBreakerName("orders")
                .getCircuitBreakerEvents())
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.getCircuitBreakerName()).isEqualTo("orders"));
        assertThat(circuitBreakerEventsEndpoint
                .getEventsFilteredByCircuitBreakerNameAndEventType("orders", "error")
                .getCircuitBreakerEvents())
                .extracting(event -> event.getType())
                .containsOnly(CircuitBreakerEvent.Type.ERROR);

        EventConsumerRegistry<RetryEvent> retryEvents = new DefaultEventConsumerRegistry<>();
        Retry retry = Retry.of("remote", RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ZERO).build());
        retry.getEventPublisher().onEvent(registerConsumer(retryEvents, "remote"));
        assertThrows(IllegalStateException.class,
                Retry.decorateCheckedSupplier(retry, () -> {
                    throw new IllegalStateException("retry me");
                })::get);
        RetryEventsEndpoint retryEventsEndpoint = new RetryEventsEndpoint(retryEvents);
        assertThat(retryEventsEndpoint.getAllRetryEvents().getRetryEvents()).isNotEmpty();
        assertThat(retryEventsEndpoint.getEventsFilteredByRetryNameAndEventType("remote", "retry").getRetryEvents())
                .extracting(event -> event.getType())
                .containsOnly(RetryEvent.Type.RETRY);

        EventConsumerRegistry<RateLimiterEvent> rateLimiterEvents = new DefaultEventConsumerRegistry<>();
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofHours(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiter rateLimiter = RateLimiter.of("api", rateLimiterConfig);
        rateLimiter.getEventPublisher().onEvent(registerConsumer(rateLimiterEvents, "api"));
        RateLimiter.decorateCheckedRunnable(rateLimiter,
                () -> assertThat(rateLimiter.getName()).isEqualTo("api")).run();
        assertThrows(RequestNotPermitted.class, RateLimiter.decorateCheckedRunnable(rateLimiter,
                () -> assertThat(rateLimiter.getName()).isEqualTo("api"))::run);
        RateLimiterEventsEndpoint rateLimiterEventsEndpoint = new RateLimiterEventsEndpoint(rateLimiterEvents);
        assertThat(rateLimiterEventsEndpoint.getAllRateLimiterEvents().getRateLimiterEvents()).isNotEmpty();
        assertThat(rateLimiterEventsEndpoint
                .getEventsFilteredByRateLimiterNameAndEventType("api", "failed_acquire")
                .getRateLimiterEvents())
                .extracting(event -> event.getType())
                .containsOnly(RateLimiterEvent.Type.FAILED_ACQUIRE);

        EventConsumerRegistry<BulkheadEvent> bulkheadEvents = new DefaultEventConsumerRegistry<>();
        Bulkhead bulkhead = Bulkhead.of("worker", BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build());
        bulkhead.getEventPublisher().onEvent(registerConsumer(bulkheadEvents, "worker"));
        Bulkhead.decorateCheckedRunnable(bulkhead,
                () -> assertThat(bulkhead.getName()).isEqualTo("worker")).run();
        BulkheadEventsEndpoint bulkheadEventsEndpoint = new BulkheadEventsEndpoint(bulkheadEvents);
        assertThat(bulkheadEventsEndpoint.getAllBulkheadEvents().getBulkheadEvents()).isNotEmpty();
        assertThat(bulkheadEventsEndpoint.getEventsFilteredByBulkheadNameAndEventType("worker", "call_finished")
                .getBulkheadEvents())
                .extracting(event -> event.getType())
                .containsOnly(BulkheadEvent.Type.CALL_FINISHED);

        EventConsumerRegistry<TimeLimiterEvent> timeLimiterEvents = new DefaultEventConsumerRegistry<>();
        TimeLimiter timeLimiter = TimeLimiter.ofDefaults("async");
        timeLimiter.getEventPublisher().onEvent(registerConsumer(timeLimiterEvents, "async"));
        timeLimiter.onSuccess();
        TimeLimiterEventsEndpoint timeLimiterEventsEndpoint = new TimeLimiterEventsEndpoint(timeLimiterEvents);
        assertThat(timeLimiterEventsEndpoint.getAllTimeLimiterEvents().getTimeLimiterEvents()).isNotEmpty();
        assertThat(timeLimiterEventsEndpoint.getEventsFilteredByTimeLimiterNameAndEventType("async", "success")
                .getTimeLimiterEvents())
                .extracting(event -> event.getType())
                .containsOnly(TimeLimiterEvent.Type.SUCCESS);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EventConsumerRegistry<TimerEvent> timerEvents = new DefaultEventConsumerRegistry<>();
        Timer timer = Timer.of("outbound", meterRegistry);
        timer.getEventPublisher().onEvent(registerConsumer(timerEvents, "outbound"));
        Timer.decorateRunnable(timer,
                () -> assertThat(timer.getName()).isEqualTo("outbound")).run();
        TimerEventsEndpoint timerEventsEndpoint = new TimerEventsEndpoint(timerEvents);
        assertThat(timerEventsEndpoint.getAllTimerEvents().getTimerEvents()).isNotEmpty();
        assertThat(timerEventsEndpoint.getEventsFilteredByTimerNameAndEventType("outbound", "success")
                .getTimerEvents())
                .extracting(event -> event.getType())
                .containsOnly(TimerEvent.Type.SUCCESS);
    }

    @Test
    void circuitBreakerHealthIndicatorReportsOnlyRegisteredBackendsAndFailsWhenConfigured() {
        CircuitBreakerConfig openOnFailure = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(1)
                .failureRateThreshold(50.0f)
                .build();
        CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(openOnFailure);
        CircuitBreaker orders = circuitBreakers.circuitBreaker("orders");
        circuitBreakers.circuitBreaker("ignored");

        CircuitBreakerProperties circuitBreakerProperties = new CircuitBreakerProperties();
        CommonCircuitBreakerConfigurationProperties.InstanceProperties ordersInstance =
                new CommonCircuitBreakerConfigurationProperties.InstanceProperties();
        ordersInstance.setRegisterHealthIndicator(true);
        ordersInstance.setAllowHealthIndicatorToFail(true);
        circuitBreakerProperties.getInstances().put("orders", ordersInstance);
        CommonCircuitBreakerConfigurationProperties.InstanceProperties ignoredInstance =
                new CommonCircuitBreakerConfigurationProperties.InstanceProperties();
        ignoredInstance.setRegisterHealthIndicator(false);
        circuitBreakerProperties.getInstances().put("ignored", ignoredInstance);

        orders.onError(0, TimeUnit.NANOSECONDS, new IllegalStateException("failure"));
        assertThat(orders.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        CircuitBreakersHealthIndicator healthIndicator = new CircuitBreakersHealthIndicator(circuitBreakers,
                circuitBreakerProperties, statuses -> statuses.contains(Status.DOWN) ? Status.DOWN : Status.UP);
        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnlyKeys("orders");
        assertThat(health.getDetails().get("orders")).isInstanceOf(Health.class);
        Health ordersHealth = (Health) health.getDetails().get("orders");
        assertThat(ordersHealth.getStatus()).isEqualTo(Status.DOWN);
        assertThat(ordersHealth.getDetails())
                .containsEntry("state", CircuitBreaker.State.OPEN)
                .containsEntry("bufferedCalls", 1)
                .containsEntry("failedCalls", 1)
                .containsKeys("failureRate", "failureRateThreshold", "notPermittedCalls");
    }

    @Test
    void rateLimiterHealthIndicatorReportsRateLimitedBackends() throws InterruptedException {
        RateLimiterConfig onePermitPerRefreshPeriod = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(2))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        RateLimiterRegistry rateLimiters = RateLimiterRegistry.of(onePermitPerRefreshPeriod);
        RateLimiter api = rateLimiters.rateLimiter("api");
        RateLimiter background = rateLimiters.rateLimiter("background");
        rateLimiters.rateLimiter("ignored");

        RateLimiterProperties rateLimiterProperties = new RateLimiterProperties();
        CommonRateLimiterConfigurationProperties.InstanceProperties apiInstance =
                new CommonRateLimiterConfigurationProperties.InstanceProperties();
        apiInstance.setRegisterHealthIndicator(true);
        apiInstance.setAllowHealthIndicatorToFail(true);
        rateLimiterProperties.getInstances().put("api", apiInstance);
        CommonRateLimiterConfigurationProperties.InstanceProperties backgroundInstance =
                new CommonRateLimiterConfigurationProperties.InstanceProperties();
        backgroundInstance.setRegisterHealthIndicator(true);
        backgroundInstance.setAllowHealthIndicatorToFail(false);
        rateLimiterProperties.getInstances().put("background", backgroundInstance);
        CommonRateLimiterConfigurationProperties.InstanceProperties ignoredInstance =
                new CommonRateLimiterConfigurationProperties.InstanceProperties();
        ignoredInstance.setRegisterHealthIndicator(false);
        rateLimiterProperties.getInstances().put("ignored", ignoredInstance);

        assertThat(api.acquirePermission()).isTrue();
        assertThat(background.acquirePermission()).isTrue();

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Future<Boolean> apiPermission = executorService.submit((Callable<Boolean>) api::acquirePermission);
        Future<Boolean> backgroundPermission = executorService.submit((Callable<Boolean>) background::acquirePermission);
        try {
            assertThat(waitingThreadsAreVisible(api, background)).isTrue();
            api.changeTimeoutDuration(Duration.ZERO);
            background.changeTimeoutDuration(Duration.ZERO);

            RateLimitersHealthIndicator healthIndicator = new RateLimitersHealthIndicator(rateLimiters,
                    rateLimiterProperties, statuses -> statuses.contains(Status.DOWN) ? Status.DOWN : Status.UP);
            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsOnlyKeys("api", "background");
            Health apiHealth = (Health) health.getDetails().get("api");
            assertThat(apiHealth.getStatus()).isEqualTo(Status.DOWN);
            assertThat(apiHealth.getDetails()).containsEntry("numberOfWaitingThreads", 1);
            assertThat(((Number) apiHealth.getDetails().get("availablePermissions")).intValue())
                    .isLessThanOrEqualTo(0);
            Health backgroundHealth = (Health) health.getDetails().get("background");
            assertThat(backgroundHealth.getStatus()).isEqualTo(new Status("RATE_LIMITED"));
            assertThat(backgroundHealth.getDetails()).containsEntry("numberOfWaitingThreads", 1);
            assertThat(((Number) backgroundHealth.getDetails().get("availablePermissions")).intValue())
                    .isLessThanOrEqualTo(0);
        } finally {
            apiPermission.cancel(true);
            backgroundPermission.cancel(true);
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void springBootPropertiesCreateResilience4jCoreConfigs() {
        CircuitBreakerProperties circuitBreakerProperties = new CircuitBreakerProperties();
        CommonCircuitBreakerConfigurationProperties.InstanceProperties circuitBreakerInstance =
                new CommonCircuitBreakerConfigurationProperties.InstanceProperties();
        circuitBreakerInstance.setSlidingWindowSize(4);
        circuitBreakerInstance.setMinimumNumberOfCalls(2);
        circuitBreakerInstance.setFailureRateThreshold(25.0f);
        circuitBreakerProperties.getInstances().put("orders", circuitBreakerInstance);
        CircuitBreakerConfig circuitBreakerConfig = circuitBreakerProperties.createCircuitBreakerConfig("orders",
                circuitBreakerProperties.getInstances().get("orders"), emptyCustomizer());
        assertThat(circuitBreakerConfig.getSlidingWindowSize()).isEqualTo(4);
        assertThat(circuitBreakerConfig.getMinimumNumberOfCalls()).isEqualTo(2);
        assertThat(circuitBreakerConfig.getFailureRateThreshold()).isEqualTo(25.0f);

        RetryProperties retryProperties = new RetryProperties();
        CommonRetryConfigurationProperties.InstanceProperties retryInstance =
                new CommonRetryConfigurationProperties.InstanceProperties();
        retryInstance.setMaxAttempts(3);
        retryInstance.setWaitDuration(Duration.ofMillis(5));
        retryProperties.getInstances().put("remote", retryInstance);
        RetryConfig retryConfig = retryProperties.createRetryConfig(retryInstance, emptyCustomizer(), "remote");
        assertThat(retryConfig.getMaxAttempts()).isEqualTo(3);
        assertThat(retryConfig.getIntervalBiFunction().apply(1, Either.right("result"))).isEqualTo(5L);

        RateLimiterProperties rateLimiterProperties = new RateLimiterProperties();
        CommonRateLimiterConfigurationProperties.InstanceProperties rateLimiterInstance =
                new CommonRateLimiterConfigurationProperties.InstanceProperties();
        rateLimiterInstance.setLimitForPeriod(7);
        rateLimiterInstance.setLimitRefreshPeriod(Duration.ofMillis(100));
        rateLimiterInstance.setTimeoutDuration(Duration.ofMillis(1));
        rateLimiterProperties.getInstances().put("api", rateLimiterInstance);
        RateLimiterConfig configuredRateLimiter = rateLimiterProperties.createRateLimiterConfig("api",
                emptyCustomizer());
        assertThat(configuredRateLimiter.getLimitForPeriod()).isEqualTo(7);
        assertThat(configuredRateLimiter.getLimitRefreshPeriod()).isEqualTo(Duration.ofMillis(100));
        assertThat(configuredRateLimiter.getTimeoutDuration()).isEqualTo(Duration.ofMillis(1));

        BulkheadProperties bulkheadProperties = new BulkheadProperties();
        CommonBulkheadConfigurationProperties.InstanceProperties bulkheadInstance =
                new CommonBulkheadConfigurationProperties.InstanceProperties();
        bulkheadInstance.setMaxConcurrentCalls(2);
        bulkheadInstance.setMaxWaitDuration(Duration.ZERO);
        bulkheadProperties.getInstances().put("worker", bulkheadInstance);
        BulkheadConfig configuredBulkhead = bulkheadProperties.createBulkheadConfig(bulkheadInstance,
                emptyCustomizer(), "worker");
        assertThat(configuredBulkhead.getMaxConcurrentCalls()).isEqualTo(2);
        assertThat(configuredBulkhead.getMaxWaitDuration()).isEqualTo(Duration.ZERO);

        TimeLimiterProperties timeLimiterProperties = new TimeLimiterProperties();
        CommonTimeLimiterConfigurationProperties.InstanceProperties timeLimiterInstance =
                new CommonTimeLimiterConfigurationProperties.InstanceProperties();
        timeLimiterInstance.setTimeoutDuration(Duration.ofMillis(50));
        timeLimiterInstance.setCancelRunningFuture(true);
        timeLimiterProperties.getInstances().put("async", timeLimiterInstance);
        TimeLimiterConfig configuredTimeLimiter = timeLimiterProperties.createTimeLimiterConfig("async");
        assertThat(configuredTimeLimiter.getTimeoutDuration()).isEqualTo(Duration.ofMillis(50));
        assertThat(configuredTimeLimiter.shouldCancelRunningFuture()).isTrue();

        TimerProperties timerProperties = new TimerProperties();
        CommonTimerConfigurationProperties.InstanceProperties timerInstance =
                new CommonTimerConfigurationProperties.InstanceProperties();
        timerInstance.setMetricNames("calls");
        timerProperties.getInstances().put("outbound", timerInstance);
        TimerConfig configuredTimer = timerProperties.createTimerConfig("outbound", emptyCustomizer());
        assertThat(configuredTimer.getMetricNames()).isEqualTo("calls");
    }

    @Test
    void customizersAreAppliedWhenPropertiesBuildConfigs() {
        CircuitBreakerProperties circuitBreakerProperties = new CircuitBreakerProperties();
        AtomicInteger circuitBreakerCustomizations = new AtomicInteger();
        CircuitBreakerConfig circuitBreakerConfig = circuitBreakerProperties.createCircuitBreakerConfig("orders", null,
                new CompositeCustomizer<>(Collections.singletonList(new CircuitBreakerConfigCustomizer() {
                    @Override
                    public String name() {
                        return "orders";
                    }

                    @Override
                    public void customize(CircuitBreakerConfig.Builder builder) {
                        circuitBreakerCustomizations.incrementAndGet();
                        builder.slidingWindowSize(6);
                    }
                })));
        assertThat(circuitBreakerCustomizations).hasValue(1);
        assertThat(circuitBreakerConfig.getSlidingWindowSize()).isEqualTo(6);

        RetryProperties retryProperties = new RetryProperties();
        RetryConfig retryConfig = retryProperties.createRetryConfig(null,
                new CompositeCustomizer<>(Collections.singletonList(new RetryConfigCustomizer() {
                    @Override
                    public String name() {
                        return "remote";
                    }

                    @Override
                    public void customize(RetryConfig.Builder builder) {
                        builder.maxAttempts(4);
                    }
                })), "remote");
        assertThat(retryConfig.getMaxAttempts()).isEqualTo(4);

        RateLimiterProperties rateLimiterProperties = new RateLimiterProperties();
        RateLimiterConfig rateLimiterConfig = rateLimiterProperties.createRateLimiterConfig("api",
                new CompositeCustomizer<>(Collections.singletonList(new RateLimiterConfigCustomizer() {
                    @Override
                    public String name() {
                        return "api";
                    }

                    @Override
                    public void customize(RateLimiterConfig.Builder builder) {
                        builder.limitForPeriod(9);
                    }
                })));
        assertThat(rateLimiterConfig.getLimitForPeriod()).isEqualTo(9);

        BulkheadProperties bulkheadProperties = new BulkheadProperties();
        BulkheadConfig bulkheadConfig = bulkheadProperties.createBulkheadConfig(null,
                new CompositeCustomizer<>(Collections.singletonList(new BulkheadConfigCustomizer() {
                    @Override
                    public String name() {
                        return "worker";
                    }

                    @Override
                    public void customize(BulkheadConfig.Builder builder) {
                        builder.maxConcurrentCalls(3);
                    }
                })), "worker");
        assertThat(bulkheadConfig.getMaxConcurrentCalls()).isEqualTo(3);

        TimeLimiterProperties timeLimiterProperties = new TimeLimiterProperties();
        TimeLimiterConfig timeLimiterConfig = timeLimiterProperties.createTimeLimiterConfig("async", null,
                new CompositeCustomizer<>(Collections.singletonList(new TimeLimiterConfigCustomizer() {
                    @Override
                    public String name() {
                        return "async";
                    }

                    @Override
                    public void customize(TimeLimiterConfig.Builder builder) {
                        builder.timeoutDuration(Duration.ofMillis(20));
                    }
                })));
        assertThat(timeLimiterConfig.getTimeoutDuration()).isEqualTo(Duration.ofMillis(20));

        TimerProperties timerProperties = new TimerProperties();
        TimerConfig timerConfig = timerProperties.createTimerConfig(null,
                new CompositeCustomizer<>(Collections.singletonList(new TimerConfigCustomizer() {
                    @Override
                    public String name() {
                        return "outbound";
                    }

                    @Override
                    public void customize(TimerConfig.Builder builder) {
                        builder.metricNames("custom.calls");
                    }
                })), "outbound");
        assertThat(timerConfig.getMetricNames()).isEqualTo("custom.calls");
    }

    private static boolean waitingThreadsAreVisible(RateLimiter... rateLimiters) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            boolean allWaiting = true;
            for (RateLimiter rateLimiter : rateLimiters) {
                if (rateLimiter.getMetrics().getNumberOfWaitingThreads() == 0) {
                    allWaiting = false;
                    break;
                }
            }
            if (allWaiting) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return false;
    }

    private static CircuitBreaker circuitBreakerWithEvents() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(1)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .build();
        return CircuitBreaker.of("orders", config);
    }

    private static <T> CircularEventConsumer<T> registerConsumer(EventConsumerRegistry<T> registry, String name) {
        return registry.createEventConsumer(name, 16);
    }

    private static <T extends CustomizerWithName> CompositeCustomizer<T> emptyCustomizer() {
        return new CompositeCustomizer<>(Collections.emptyList());
    }
}
