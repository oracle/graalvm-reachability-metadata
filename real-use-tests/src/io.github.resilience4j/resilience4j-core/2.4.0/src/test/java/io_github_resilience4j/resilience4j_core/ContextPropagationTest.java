/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.ContextAwareScheduledThreadPoolExecutor;
import io.github.resilience4j.core.ContextPropagator;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propagates request context through Resilience4j's context-aware executor subsystem.
 *
 * An application schedules work for a request and expects the worker to see the request identifier,
 * while the executor clears that context before the worker is released.
 */
class ContextPropagationTest {

    @Test
    void makesRequestContextAvailableToScheduledWork() throws InterruptedException {
        ThreadLocal<String> requestId = new ThreadLocal<>();
        ContextPropagator<String> requestContext = new ContextPropagator<>() {
            @Override
            public Supplier<Optional<String>> retrieve() {
                return () -> Optional.ofNullable(requestId.get());
            }

            @Override
            public Consumer<Optional<String>> copy() {
                return value -> value.ifPresentOrElse(requestId::set, requestId::remove);
            }

            @Override
            public Consumer<Optional<String>> clear() {
                return ignored -> requestId.remove();
            }
        };
        ContextAwareScheduledThreadPoolExecutor executor = ContextAwareScheduledThreadPoolExecutor
                .newScheduledThreadPool()
                .corePoolSize(1)
                .contextPropagators(requestContext)
                .build();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> workerRequestId = new AtomicReference<>();

        try {
            requestId.set("request-42");
            executor.schedule(() -> {
                workerRequestId.set(requestId.get());
                completed.countDown();
            }, 0, TimeUnit.MILLISECONDS);

            assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(workerRequestId).hasValue("request-42");
        } finally {
            requestId.remove();
            executor.shutdownNow();
        }
    }
}
