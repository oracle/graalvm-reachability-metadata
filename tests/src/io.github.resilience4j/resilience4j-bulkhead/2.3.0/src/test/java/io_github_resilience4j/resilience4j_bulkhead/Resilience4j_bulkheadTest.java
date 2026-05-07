/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_bulkhead;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import io.github.resilience4j.core.ContextPropagator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Resilience4j_bulkheadTest {
    private static final Duration NO_WAIT = Duration.ZERO;
    private static final ThreadLocal<String> REQUEST_CONTEXT = new ThreadLocal<>();

    @Test
    void semaphoreBulkheadLimitsConcurrentCallsAndPublishesEvents() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(NO_WAIT)
            .writableStackTraceEnabled(false)
            .build();
        Bulkhead bulkhead = Bulkhead.of("payments", config, Map.of("component", "checkout"));
        AtomicInteger permittedCalls = new AtomicInteger();
        AtomicInteger rejectedCalls = new AtomicInteger();
        AtomicInteger finishedCalls = new AtomicInteger();
        List<BulkheadEvent.Type> eventTypes = new ArrayList<>();

        bulkhead.getEventPublisher()
            .onCallPermitted(event -> {
                permittedCalls.incrementAndGet();
                eventTypes.add(event.getEventType());
                assertThat(event.getBulkheadName()).isEqualTo("payments");
            })
            .onCallRejected(event -> {
                rejectedCalls.incrementAndGet();
                eventTypes.add(event.getEventType());
                assertThat(event.toString()).contains("payments", "rejected");
            })
            .onCallFinished(event -> {
                finishedCalls.incrementAndGet();
                eventTypes.add(event.getEventType());
            });

        assertThat(bulkhead.getName()).isEqualTo("payments");
        assertThat(bulkhead.getTags()).containsEntry("component", "checkout");
        assertThat(bulkhead.getMetrics().getMaxAllowedConcurrentCalls()).isEqualTo(1);
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isZero();
        assertThat(bulkhead.tryAcquirePermission()).isFalse();
        assertThatThrownBy(bulkhead::acquirePermission)
            .isInstanceOf(BulkheadFullException.class)
            .hasMessageContaining("payments")
            .satisfies(throwable -> assertThat(throwable.getStackTrace()).isEmpty());

        bulkhead.onComplete();

        assertThat(permittedCalls).hasValue(1);
        assertThat(rejectedCalls).hasValue(2);
        assertThat(finishedCalls).hasValue(1);
        assertThat(eventTypes).containsExactly(
            BulkheadEvent.Type.CALL_PERMITTED,
            BulkheadEvent.Type.CALL_REJECTED,
            BulkheadEvent.Type.CALL_REJECTED,
            BulkheadEvent.Type.CALL_FINISHED);
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
    }

    @Test
    void decoratedCompletionStageReleasesPermissionAfterAsynchronousCompletion() throws Exception {
        Bulkhead bulkhead = Bulkhead.of("async-semaphore", BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(NO_WAIT)
            .build());
        CompletableFuture<String> backendResult = new CompletableFuture<>();
        Supplier<CompletionStage<String>> protectedCall = Bulkhead.decorateCompletionStage(
            bulkhead, () -> backendResult);

        CompletionStage<String> firstCall = protectedCall.get();
        CompletionStage<String> rejectedCall = protectedCall.get();

        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isZero();
        assertThatThrownBy(() -> rejectedCall.toCompletableFuture().join())
            .hasCauseInstanceOf(BulkheadFullException.class);

        backendResult.complete("completed");

        assertThat(firstCall.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo("completed");
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);

        CompletionStage<String> failedCall = Bulkhead.<String>decorateCompletionStage(bulkhead, () -> {
            throw new IllegalStateException("backend not started");
        }).get();

        assertThatThrownBy(() -> failedCall.toCompletableFuture().join())
            .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
    }

    @Test
    void registryCreatesTaggedSingletonsFromNamedConfigurations() {
        BulkheadConfig defaultConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(2)
            .maxWaitDuration(NO_WAIT)
            .build();
        BulkheadConfig singleCallConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(NO_WAIT)
            .fairCallHandlingStrategyEnabled(false)
            .build();
        BulkheadRegistry registry = BulkheadRegistry.custom()
            .withBulkheadConfig(defaultConfig)
            .addBulkheadConfig("single-call", singleCallConfig)
            .withTags(Map.of("environment", "test", "owner", "registry"))
            .build();

        Bulkhead defaultBulkhead = registry.bulkhead("default-service");
        Bulkhead namedBulkhead = registry.bulkhead("named-service", "single-call", Map.of("owner", "test"));
        Bulkhead sameNamedBulkhead = registry.bulkhead("named-service", "single-call");

        assertThat(defaultBulkhead.getMetrics().getMaxAllowedConcurrentCalls()).isEqualTo(2);
        assertThat(namedBulkhead).isSameAs(sameNamedBulkhead);
        assertThat(namedBulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(1);
        assertThat(namedBulkhead.getBulkheadConfig().isFairCallHandlingEnabled()).isFalse();
        assertThat(namedBulkhead.getTags())
            .containsEntry("environment", "test")
            .containsEntry("owner", "test");
        assertThat(registry.getAllBulkheads())
            .extracting(Bulkhead::getName)
            .containsExactlyInAnyOrder("default-service", "named-service");
    }

    @Test
    void changingSemaphoreBulkheadConfigUpdatesCapacityWithoutRecreatingInstance() {
        BulkheadConfig initialConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(NO_WAIT)
            .build();
        Bulkhead bulkhead = Bulkhead.of("resizable", initialConfig);
        BulkheadConfig expandedConfig = BulkheadConfig.from(initialConfig)
            .maxConcurrentCalls(2)
            .build();

        bulkhead.changeConfig(expandedConfig);

        assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(2);
        assertThat(bulkhead.getMetrics().getMaxAllowedConcurrentCalls()).isEqualTo(2);
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        assertThat(bulkhead.tryAcquirePermission()).isFalse();

        bulkhead.onComplete();
        bulkhead.onComplete();
        bulkhead.changeConfig(initialConfig);

        assertThat(bulkhead.getMetrics().getMaxAllowedConcurrentCalls()).isEqualTo(1);
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
    }

    @Test
    void threadPoolBulkheadExecutesQueuedWorkRejectsOverflowAndPropagatesContext() throws Exception {
        ThreadPoolBulkheadConfig config = ThreadPoolBulkheadConfig.custom()
            .coreThreadPoolSize(1)
            .maxThreadPoolSize(1)
            .queueCapacity(1)
            .keepAliveDuration(Duration.ofMillis(10))
            .writableStackTraceEnabled(false)
            .contextPropagator(new ThreadLocalContextPropagator())
            .build();
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);
        AtomicInteger permittedCalls = new AtomicInteger();
        AtomicInteger rejectedCalls = new AtomicInteger();
        AtomicInteger finishedCalls = new AtomicInteger();

        try (ThreadPoolBulkhead bulkhead = ThreadPoolBulkhead.of("worker-pool", config, Map.of("kind", "bounded"))) {
            bulkhead.getEventPublisher()
                .onCallPermitted(event -> permittedCalls.incrementAndGet())
                .onCallRejected(event -> rejectedCalls.incrementAndGet())
                .onCallFinished(event -> finishedCalls.incrementAndGet());
            REQUEST_CONTEXT.set("request-42");

            CompletionStage<String> first = bulkhead.executeSupplier(() -> {
                firstTaskStarted.countDown();
                assertThat(REQUEST_CONTEXT.get()).isEqualTo("request-42");
                await(releaseFirstTask);
                return "first";
            });
            assertThat(firstTaskStarted.await(5, TimeUnit.SECONDS)).isTrue();

            CompletionStage<String> queued = bulkhead.executeSupplier(() -> {
                assertThat(REQUEST_CONTEXT.get()).isEqualTo("request-42");
                return "queued";
            });

            assertThat(bulkhead.getName()).isEqualTo("worker-pool");
            assertThat(bulkhead.getTags()).containsEntry("kind", "bounded");
            assertThat(bulkhead.getMetrics().getCoreThreadPoolSize()).isEqualTo(1);
            assertThat(bulkhead.getMetrics().getMaximumThreadPoolSize()).isEqualTo(1);
            assertThat(bulkhead.getMetrics().getQueueCapacity()).isEqualTo(1);
            assertThat(bulkhead.getMetrics().getQueueDepth()).isEqualTo(1);
            assertThat(bulkhead.getMetrics().getRemainingQueueCapacity()).isZero();
            assertThatThrownBy(() -> bulkhead.executeSupplier(() -> "overflow"))
                .isInstanceOf(BulkheadFullException.class)
                .hasMessageContaining("worker-pool")
                .satisfies(throwable -> assertThat(throwable.getStackTrace()).isEmpty());

            releaseFirstTask.countDown();

            assertThat(first.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(queued.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo("queued");
            assertThat(permittedCalls).hasValue(2);
            assertThat(rejectedCalls).hasValue(1);
            assertThat(finishedCalls).hasValue(2);
        } finally {
            REQUEST_CONTEXT.remove();
            releaseFirstTask.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Timed out waiting for latch", exception);
        }
    }

    private static final class ThreadLocalContextPropagator implements ContextPropagator<String> {
        @Override
        public Supplier<Optional<String>> retrieve() {
            String value = REQUEST_CONTEXT.get();
            return () -> Optional.ofNullable(value);
        }

        @Override
        public Consumer<Optional<String>> copy() {
            return value -> value.ifPresentOrElse(REQUEST_CONTEXT::set, REQUEST_CONTEXT::remove);
        }

        @Override
        public Consumer<Optional<String>> clear() {
            return ignored -> REQUEST_CONTEXT.remove();
        }
    }
}
