/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_circuitbreaker_resilience4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4jBulkheadProvider;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4jBulkheadConfigurationBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4jBulkheadProvider;

public class Spring_cloud_circuitbreaker_resilience4jTest {

    @Test
    void configBuilderKeepsCircuitBreakerAndTimeLimiterConfiguration() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(25.0f)
            .build();
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(250))
            .cancelRunningFuture(true)
            .build();

        Resilience4JConfigBuilder.Resilience4JCircuitBreakerConfiguration configuration = new Resilience4JConfigBuilder(
                "inventory")
            .circuitBreakerConfig(circuitBreakerConfig)
            .timeLimiterConfig(timeLimiterConfig)
            .build();

        assertThat(configuration.getId()).isEqualTo("inventory");
        assertThat(configuration.getCircuitBreakerConfig()).isSameAs(circuitBreakerConfig);
        assertThat(configuration.getTimeLimiterConfig()).isSameAs(timeLimiterConfig);
    }

    @Test
    void circuitBreakerFactoryRunsSuppliersAndAppliesFallbacks() {
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setDisableTimeLimiter(true);
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(circuitBreakerRegistry,
                timeLimiterRegistry, null, properties);
        DirectExecutorService executorService = new DirectExecutorService();
        AtomicReference<String> customizedName = new AtomicReference<>();
        factory.configureExecutorService(executorService);
        factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
            .timeLimiterConfig(TimeLimiterConfig.ofDefaults())
            .build());
        factory.addCircuitBreakerCustomizer(circuitBreaker -> customizedName.set(circuitBreaker.getName()), "catalog");

        try {
            CircuitBreaker circuitBreaker = factory.create("catalog");

            String successfulValue = circuitBreaker.run(() -> "available", throwable -> "fallback");
            String fallbackValue = circuitBreaker.run(() -> {
                throw new IllegalStateException("backend failed");
            }, throwable -> "fallback: " + throwable.getClass().getSimpleName());

            assertThat(successfulValue).isEqualTo("available");
            assertThat(fallbackValue).contains("fallback");
            assertThat(customizedName).hasValue("catalog");
            assertThat(circuitBreakerRegistry.find("catalog")).isPresent();
            assertThat(timeLimiterRegistry.find("catalog")).isEmpty();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void circuitBreakerOpensAfterConfiguredFailures() {
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setDisableTimeLimiter(true);
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(circuitBreakerRegistry,
                TimeLimiterRegistry.ofDefaults(), null, properties);
        DirectExecutorService executorService = new DirectExecutorService();
        AtomicBoolean rejectedCallWasInvoked = new AtomicBoolean();
        factory.configureExecutorService(executorService);
        factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build())
            .timeLimiterConfig(TimeLimiterConfig.ofDefaults())
            .build());

        try {
            CircuitBreaker circuitBreaker = factory.create("payments");

            circuitBreaker.run(() -> failBackend(), throwable -> "first fallback");
            circuitBreaker.run(() -> failBackend(), throwable -> "second fallback");
            Object rejected = circuitBreaker.run(() -> {
                rejectedCallWasInvoked.set(true);
                return "should not run";
            }, throwable -> throwable);

            assertThat(rejected).isInstanceOf(CallNotPermittedException.class);
            assertThat(rejectedCallWasInvoked).isFalse();
            assertThat(circuitBreakerRegistry.circuitBreaker("payments").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void circuitBreakerAppliesTimeLimiterFallbackWhenSupplierExceedsTimeout() throws InterruptedException {
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(circuitBreakerRegistry,
                timeLimiterRegistry, null, new Resilience4JConfigurationProperties());
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        factory.configureExecutorService(executorService);
        factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(100))
                .cancelRunningFuture(true)
                .build())
            .build());

        try {
            CircuitBreaker circuitBreaker = factory.create("slow-service");
            AtomicReference<Throwable> fallbackThrowable = new AtomicReference<>();

            String value = circuitBreaker.run(() -> {
                try {
                    Thread.sleep(1_000);
                    return "slow response";
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return "interrupted";
                }
            }, throwable -> {
                fallbackThrowable.set(throwable);
                return "fallback";
            });

            assertThat(value).isEqualTo("fallback");
            assertThat(fallbackThrowable.get()).isInstanceOf(TimeoutException.class);
            assertThat(timeLimiterRegistry.find("slow-service")).isPresent();
            assertThat(circuitBreakerRegistry.find("slow-service")).isPresent();
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void groupConfigurationSelectsGroupRegistriesAndCreatesTimeLimiter() {
        CircuitBreakerConfig groupCircuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(3)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(33.0f)
            .build();
        TimeLimiterConfig groupTimeLimiterConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(500))
            .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        circuitBreakerRegistry.addConfiguration("orders", groupCircuitBreakerConfig);
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        timeLimiterRegistry.addConfiguration("orders", groupTimeLimiterConfig);
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(circuitBreakerRegistry,
                timeLimiterRegistry, null, new Resilience4JConfigurationProperties());
        DirectExecutorService executorService = new DirectExecutorService();
        DirectExecutorService groupExecutorService = new DirectExecutorService();
        factory.configureExecutorService(executorService);
        factory.configureGroupExecutorService(group -> groupExecutorService);

        try {
            CircuitBreaker circuitBreaker = factory.create("create-order", "orders");

            String value = circuitBreaker.run(() -> "created", throwable -> "fallback");

            assertThat(value).isEqualTo("created");
            assertThat(circuitBreakerRegistry.circuitBreaker("create-order")
                .getCircuitBreakerConfig()
                .getFailureRateThreshold()).isEqualTo(33.0f);
            TimeLimiter timeLimiter = timeLimiterRegistry.find("create-order").orElseThrow();
            assertThat(timeLimiter.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofMillis(500));
        } finally {
            executorService.shutdownNow();
            groupExecutorService.shutdownNow();
        }
    }

    @Test
    void circuitBreakerFactoryRejectsBlankIdentifiers() {
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null,
                new Resilience4JConfigurationProperties());

        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> factory.create("catalog", " "));
    }

    @Test
    void bulkheadConfigurationBuilderKeepsConfiguredSemaphoreAndThreadPoolSettings() {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ZERO)
            .build();
        ThreadPoolBulkheadConfig threadPoolBulkheadConfig = ThreadPoolBulkheadConfig.custom()
            .coreThreadPoolSize(1)
            .maxThreadPoolSize(1)
            .queueCapacity(1)
            .build();

        Resilience4jBulkheadConfigurationBuilder.BulkheadConfiguration configuration =
                new Resilience4jBulkheadConfigurationBuilder()
            .bulkheadConfig(null)
            .threadPoolBulkheadConfig(null)
            .bulkheadConfig(bulkheadConfig)
            .threadPoolBulkheadConfig(threadPoolBulkheadConfig)
            .build();

        assertThat(configuration.getBulkheadConfig()).isSameAs(bulkheadConfig);
        assertThat(configuration.getThreadPoolBulkheadConfig()).isSameAs(threadPoolBulkheadConfig);
    }

    @Test
    void bulkheadProviderDecoratesCallableAndRunMethodWithSemaphoreBulkhead() throws Exception {
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setEnableSemaphoreDefaultBulkhead(true);
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();
        ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry = ThreadPoolBulkheadRegistry.ofDefaults();
        Resilience4jBulkheadProvider provider = new Resilience4jBulkheadProvider(threadPoolBulkheadRegistry,
                bulkheadRegistry, properties);
        provider.configure(builder -> builder.bulkheadConfig(BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ZERO)
            .build()), "shipping");
        AtomicBoolean customizerCalled = new AtomicBoolean();
        provider.addBulkheadCustomizer(bulkhead -> customizerCalled.set("shipping".equals(bulkhead.getName())),
                "shipping");

        Callable<String> decorated = provider.decorateCallable("shipping", Map.of("group", "shipping"),
                () -> "decorated");
        String decoratedValue = decorated.call();
        String runValue = provider.run("shipping", () -> "run", throwable -> "fallback",
                CircuitBreakerRegistry.ofDefaults().circuitBreaker("shipping"), null, Map.of("group", "shipping"));

        assertThat(decoratedValue).isEqualTo("decorated");
        assertThat(runValue).isEqualTo("run");
        assertThat(customizerCalled).isTrue();
        assertThat(bulkheadRegistry.find("shipping")).isPresent();
    }

    @Test
    void reactiveFactoryRunsMonoAndFluxWithFallbacks() {
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setDisableTimeLimiter(true);
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        ReactiveResilience4JCircuitBreakerFactory factory = new ReactiveResilience4JCircuitBreakerFactory(
                circuitBreakerRegistry, TimeLimiterRegistry.ofDefaults(), null, properties);
        AtomicReference<String> customizedName = new AtomicReference<>();
        factory.addCircuitBreakerCustomizer(circuitBreaker -> customizedName.set(circuitBreaker.getName()), "reactive");

        ReactiveCircuitBreaker circuitBreaker = factory.create("reactive");

        String monoValue = circuitBreaker.run(Mono.just("mono"), throwable -> Mono.just("mono fallback"))
            .block(Duration.ofSeconds(2));
        java.util.List<String> fluxValues = circuitBreaker
            .run(Flux.just("a", "b"), throwable -> Flux.just("flux fallback"))
            .collectList()
            .block(Duration.ofSeconds(2));
        String fallbackValue = circuitBreaker
            .run(Mono.<String>error(new IllegalStateException("reactive backend failed")),
                    throwable -> Mono.just("fallback: " + throwable.getClass().getSimpleName()))
            .block(Duration.ofSeconds(2));

        assertThat(monoValue).isEqualTo("mono");
        assertThat(fluxValues).containsExactly("a", "b");
        assertThat(fallbackValue).isEqualTo("fallback: IllegalStateException");
        assertThat(customizedName).hasValue("reactive");
        assertThat(circuitBreakerRegistry.find("reactive")).isPresent();
    }

    @Test
    void reactiveFactoryAppliesTimeLimiterFallbackWhenMonoExceedsTimeout() {
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        ReactiveResilience4JCircuitBreakerFactory factory = new ReactiveResilience4JCircuitBreakerFactory(
                circuitBreakerRegistry, timeLimiterRegistry, null, new Resilience4JConfigurationProperties());
        factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
            .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(100)).build())
            .build());
        AtomicReference<Throwable> fallbackThrowable = new AtomicReference<>();

        ReactiveCircuitBreaker circuitBreaker = factory.create("slow-reactive-service");
        String value = circuitBreaker
            .run(Mono.delay(Duration.ofSeconds(1)).thenReturn("slow response"), throwable -> {
                fallbackThrowable.set(throwable);
                return Mono.just("fallback");
            })
            .block(Duration.ofSeconds(2));

        assertThat(value).isEqualTo("fallback");
        assertThat(fallbackThrowable.get()).isInstanceOf(TimeoutException.class);
        assertThat(timeLimiterRegistry.find("slow-reactive-service")).isPresent();
        assertThat(circuitBreakerRegistry.find("slow-reactive-service")).isPresent();
    }

    @Test
    void reactiveBulkheadProviderDecoratesMonoAndFlux() {
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();
        ReactiveResilience4jBulkheadProvider provider = new ReactiveResilience4jBulkheadProvider(bulkheadRegistry);
        provider.configureDefault(id -> new Resilience4jBulkheadConfigurationBuilder()
            .bulkheadConfig(BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build())
            .build());
        AtomicBoolean customizerCalled = new AtomicBoolean();
        provider.addBulkheadCustomizer(bulkhead -> customizerCalled.set("reactive-bulkhead".equals(bulkhead.getName())),
                "reactive-bulkhead");

        String monoValue = provider.decorateMono("reactive-bulkhead", Map.of("group", "reactive"), Mono.just("mono"))
            .block(Duration.ofSeconds(2));
        java.util.List<String> fluxValues = provider
            .decorateFlux("reactive-bulkhead", Map.of("group", "reactive"), Flux.just("one", "two"))
            .collectList()
            .block(Duration.ofSeconds(2));

        assertThat(monoValue).isEqualTo("mono");
        assertThat(fluxValues).containsExactly("one", "two");
        assertThat(customizerCalled).isTrue();
        assertThat(provider.getBulkheadRegistry()).isSameAs(bulkheadRegistry);
    }

    @Test
    void configurationPropertiesExposeAllConfigurationFlags() {
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        Map<String, Boolean> disableTimeLimiterMap = Map.of("fast", true, "slow", false);

        properties.setEnableGroupMeterFilter(false);
        properties.setDefaultGroupTag("unknown");
        properties.setEnableSemaphoreDefaultBulkhead(true);
        properties.setDisableThreadPool(true);
        properties.setDisableTimeLimiter(true);
        properties.setDisableTimeLimiterMap(disableTimeLimiterMap);

        assertThat(properties.isEnableGroupMeterFilter()).isFalse();
        assertThat(properties.getDefaultGroupTag()).isEqualTo("unknown");
        assertThat(properties.isEnableSemaphoreDefaultBulkhead()).isTrue();
        assertThat(properties.isDisableThreadPool()).isTrue();
        assertThat(properties.isDisableTimeLimiter()).isTrue();
        assertThat(properties.getDisableTimeLimiterMap()).isSameAs(disableTimeLimiterMap);
    }

    private static String failBackend() {
        throw new IllegalStateException("backend failed");
    }

    private static final class DirectExecutorService extends AbstractExecutorService {

        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            this.shutdown = true;
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            this.shutdown = true;
            return java.util.List.of();
        }

        @Override
        public boolean isShutdown() {
            return this.shutdown;
        }

        @Override
        public boolean isTerminated() {
            return this.shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return this.shutdown;
        }

        @Override
        public void execute(Runnable command) {
            if (this.shutdown) {
                throw new RejectedExecutionException("executor is shut down");
            }
            command.run();
        }
    }
}
