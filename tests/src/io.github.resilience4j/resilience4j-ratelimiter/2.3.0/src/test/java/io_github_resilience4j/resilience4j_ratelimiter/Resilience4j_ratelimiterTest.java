/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.core.functions.CheckedConsumer;
import io.github.resilience4j.core.functions.CheckedFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.event.RateLimiterEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Resilience4j_ratelimiterTest {
    private static final Duration LONG_REFRESH_PERIOD = Duration.ofSeconds(30);

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void createsConfigAndTracksPermissionsWithoutWaiting() {
        RateLimiterConfig baseConfig = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(LONG_REFRESH_PERIOD)
                .timeoutDuration(Duration.ZERO)
                .writableStackTraceEnabled(false)
                .build();
        RateLimiterConfig copiedConfig = RateLimiterConfig.from(baseConfig)
                .limitForPeriod(3)
                .build();
        Map<String, String> tags = Map.of("component", "payments", "tier", "gold");

        RateLimiter rateLimiter = RateLimiter.of("payments-api", copiedConfig, tags);

        assertThat(rateLimiter.getName()).isEqualTo("payments-api");
        assertThat(rateLimiter.getTags()).containsAllEntriesOf(tags);
        assertThat(rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(3);
        assertThat(rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod()).isEqualTo(LONG_REFRESH_PERIOD);
        assertThat(rateLimiter.getRateLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ZERO);
        assertThat(rateLimiter.getRateLimiterConfig().isWritableStackTraceEnabled()).isFalse();
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isEqualTo(3);
        assertThat(rateLimiter.getMetrics().getNumberOfWaitingThreads()).isZero();

        assertThat(rateLimiter.acquirePermission()).isTrue();
        assertThat(rateLimiter.acquirePermission(2)).isTrue();
        assertThat(rateLimiter.acquirePermission()).isFalse();
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isZero();

        rateLimiter.changeLimitForPeriod(4);
        rateLimiter.changeTimeoutDuration(Duration.ofMillis(25));

        assertThat(rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(4);
        assertThat(rateLimiter.getRateLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofMillis(25));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void decoratesSynchronousOperationsAndRejectsWhenPermitsAreExhausted() throws Exception {
        RateLimiter rateLimiter = RateLimiter.of("decorators", zeroTimeoutConfig(3));
        AtomicInteger calls = new AtomicInteger();

        Supplier<String> supplier = RateLimiter.decorateSupplier(rateLimiter, () -> "value-" + calls.incrementAndGet());
        Callable<String> callable = RateLimiter.decorateCallable(
                rateLimiter, () -> "callable-" + calls.incrementAndGet());
        Runnable runnable = RateLimiter.decorateRunnable(rateLimiter, calls::incrementAndGet);

        assertThat(supplier.get()).isEqualTo("value-1");
        assertThat(callable.call()).isEqualTo("callable-2");
        runnable.run();
        assertThat(calls).hasValue(3);
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isZero();

        assertThatThrownBy(supplier::get)
                .isInstanceOf(RequestNotPermitted.class)
                .hasMessageContaining("RateLimiter 'decorators' does not permit further calls");
        assertThat(calls).hasValue(3);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void decoratesFunctionsConsumersAndCheckedSuppliersWithCustomPermitCounts() throws Throwable {
        RateLimiter rateLimiter = RateLimiter.of("bulkhead", zeroTimeoutConfig(4));
        Function<String, Integer> permitsByLength = String::length;
        Function<String, String> function = RateLimiter.decorateFunction(
                rateLimiter, permitsByLength, String::toUpperCase);
        CheckedFunction<String, String> checkedFunction = RateLimiter.decorateCheckedFunction(
                rateLimiter, 1, value -> value + "-checked");
        List<String> accepted = new ArrayList<>();
        Consumer<String> consumer = RateLimiter.decorateConsumer(rateLimiter, 1, accepted::add);
        CheckedConsumer<String> checkedConsumer = RateLimiter.decorateCheckedConsumer(
                rateLimiter, permitsByLength, accepted::add);

        assertThat(function.apply("ab")).isEqualTo("AB");
        assertThat(checkedFunction.apply("first")).isEqualTo("first-checked");
        consumer.accept("consumed");
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isZero();

        assertThatExceptionOfType(RequestNotPermitted.class)
                .isThrownBy(() -> checkedConsumer.accept("xy"));
        assertThat(accepted).containsExactly("consumed");

        RateLimiter anotherLimiter = RateLimiter.of("checked-supplier", zeroTimeoutConfig(1));
        assertThat(anotherLimiter.executeCheckedSupplier(() -> "checked-result")).isEqualTo("checked-result");
        assertThatExceptionOfType(RequestNotPermitted.class)
                .isThrownBy(() -> anotherLimiter.executeCheckedSupplier(() -> "never-called"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void decoratesCompletionStagesAndFutures() throws Exception {
        RateLimiter rateLimiter = RateLimiter.of("async", zeroTimeoutConfig(2));
        Supplier<CompletionStage<String>> completionStage = RateLimiter.decorateCompletionStage(
                rateLimiter, () -> CompletableFuture.completedFuture("completed"));
        Supplier<CompletableFuture<String>> future = RateLimiter.decorateFuture(
                rateLimiter, () -> CompletableFuture.completedFuture("future"));

        assertThat(completionStage.get().toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("completed");
        assertThat(future.get().get(1, TimeUnit.SECONDS)).isEqualTo("future");

        CompletionStage<String> rejected = completionStage.get();
        assertThatThrownBy(() -> rejected.toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RequestNotPermitted.class);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void publishesSuccessFailureAndDrainEvents() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(LONG_REFRESH_PERIOD)
                .timeoutDuration(Duration.ZERO)
                .drainPermissionsOnResult(result -> result.isRight() && "drain".equals(result.get()))
                .build();
        RateLimiter rateLimiter = RateLimiter.of("events", config);
        List<RateLimiterEvent> allEvents = new ArrayList<>();
        List<RateLimiterEvent> successEvents = new ArrayList<>();
        List<RateLimiterEvent> failureEvents = new ArrayList<>();

        rateLimiter.getEventPublisher()
                .onSuccess(successEvents::add)
                .onFailure(failureEvents::add)
                .onEvent(allEvents::add);

        assertThat(rateLimiter.acquirePermission()).isTrue();
        assertThat(rateLimiter.acquirePermission(2)).isFalse();
        rateLimiter.onResult("drain");

        assertThat(successEvents).hasSize(1);
        assertThat(successEvents.get(0).getRateLimiterName()).isEqualTo("events");
        assertThat(successEvents.get(0).getNumberOfPermits()).isEqualTo(1);
        assertThat(failureEvents).hasSize(1);
        assertThat(failureEvents.get(0).getNumberOfPermits()).isEqualTo(2);
        assertThat(allEvents)
                .extracting(RateLimiterEvent::getEventType)
                .containsExactly(
                        RateLimiterEvent.Type.SUCCESSFUL_ACQUIRE,
                        RateLimiterEvent.Type.FAILED_ACQUIRE,
                        RateLimiterEvent.Type.DRAINED);
        assertThat(allEvents).allSatisfy(event -> assertThat(event.getCreationTime()).isNotNull());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void registryCreatesCachesReplacesAndRemovesRateLimiters() {
        RateLimiterConfig defaultConfig = zeroTimeoutConfig(5);
        RateLimiterConfig slowConfig = zeroTimeoutConfig(1);
        List<String> registryEvents = new ArrayList<>();
        RateLimiterRegistry registry = RateLimiterRegistry.custom()
                .withRateLimiterConfig(defaultConfig)
                .addRateLimiterConfig("slow", slowConfig)
                .withTags(Map.of("application", "checkout"))
                .build();
        registry.getEventPublisher()
                .onEntryAdded(event -> registryEvents.add("added:" + event.getAddedEntry().getName()))
                .onEntryReplaced(event -> registryEvents.add("replaced:" + event.getNewEntry().getName()))
                .onEntryRemoved(event -> registryEvents.add("removed:" + event.getRemovedEntry().getName()));

        RateLimiter fromNamedConfig = registry.rateLimiter("backendA", "slow", Map.of("backend", "A"));
        RateLimiter cached = registry.rateLimiter("backendA");

        assertThat(cached).isSameAs(fromNamedConfig);
        assertThat(fromNamedConfig.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(1);
        assertThat(fromNamedConfig.getTags()).containsEntry("backend", "A");
        assertThat(registry.getTags()).containsEntry("application", "checkout");
        assertThat(registry.getAllRateLimiters()).containsExactly(fromNamedConfig);
        assertThat(registry.find("backendA")).hasValueSatisfying(value -> assertThat(value).isSameAs(fromNamedConfig));
        assertThat(registry.getConfiguration("slow"))
                .hasValueSatisfying(value -> assertThat(value.getLimitForPeriod())
                        .isEqualTo(slowConfig.getLimitForPeriod()));
        assertThat(registry.getDefaultConfig().getLimitForPeriod()).isEqualTo(defaultConfig.getLimitForPeriod());

        registry.addConfiguration("burst", zeroTimeoutConfig(10));
        assertThat(registry.getConfiguration("burst")).isPresent();
        assertThat(registry.removeConfiguration("burst").getLimitForPeriod()).isEqualTo(10);

        RateLimiter replacement = RateLimiter.of("backendA", defaultConfig);
        Optional<RateLimiter> replaced = registry.replace("backendA", replacement);
        Optional<RateLimiter> removed = registry.remove("backendA");

        assertThat(replaced).hasValueSatisfying(value -> assertThat(value).isSameAs(fromNamedConfig));
        assertThat(removed).hasValueSatisfying(value -> assertThat(value).isSameAs(replacement));
        assertThat(registry.find("backendA")).isEmpty();
        assertThat(registryEvents).containsExactly("added:backendA", "replaced:backendA", "removed:backendA");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void staticFactoriesAndRequestNotPermittedUseConfiguredStackTraceBehavior() {
        RateLimiter defaults = RateLimiter.ofDefaults("defaults");
        RateLimiter supplied = RateLimiter.of("supplied", () -> zeroTimeoutConfig(1), Map.of("source", "supplier"));
        RateLimiter zeroStackTrace = RateLimiter.of("no-stack", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(LONG_REFRESH_PERIOD)
                .timeoutDuration(Duration.ZERO)
                .writableStackTraceEnabled(false)
                .build());

        assertThat(defaults.getRateLimiterConfig().getLimitForPeriod())
                .isEqualTo(RateLimiterConfig.ofDefaults().getLimitForPeriod());
        assertThat(defaults.getRateLimiterConfig().getLimitRefreshPeriod())
                .isEqualTo(RateLimiterConfig.ofDefaults().getLimitRefreshPeriod());
        assertThat(supplied.getTags()).containsEntry("source", "supplier");
        assertThat(supplied.reservePermission()).isZero();
        assertThat(supplied.reservePermission()).isNegative();

        zeroStackTrace.acquirePermission();
        assertThatExceptionOfType(RequestNotPermitted.class)
                .isThrownBy(() -> RateLimiter.waitForPermission(zeroStackTrace))
                .satisfies(exception -> assertThat(exception.getStackTrace()).isEmpty());
        assertThat(RequestNotPermitted.createRequestNotPermitted(zeroStackTrace))
                .hasMessageContaining("RateLimiter 'no-stack' does not permit further calls")
                .satisfies(exception -> assertThat(exception.getStackTrace()).isEmpty());
    }

    private static RateLimiterConfig zeroTimeoutConfig(int limitForPeriod) {
        return RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(LONG_REFRESH_PERIOD)
                .timeoutDuration(Duration.ZERO)
                .build();
    }
}
