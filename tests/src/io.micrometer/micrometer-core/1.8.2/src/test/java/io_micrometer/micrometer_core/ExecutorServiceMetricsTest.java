/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorServiceMetricsTest {
    @Test
    void bindsThreadPoolExecutorMetrics() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExecutorService executor = Executors.newFixedThreadPool(1);
        try {
            Future<Integer> completedTask = executor.submit(() -> 1);
            assertThat(completedTask.get(5, TimeUnit.SECONDS)).isEqualTo(1);

            new ExecutorServiceMetrics(executor, "direct", Collections.singleton(Tag.of("kind", "thread-pool")))
                    .bindTo(registry);

            FunctionCounter completed = registry.find("executor.completed")
                    .tag("name", "direct")
                    .tag("kind", "thread-pool")
                    .functionCounter();
            Gauge poolSize = registry.find("executor.pool.size")
                    .tag("name", "direct")
                    .tag("kind", "thread-pool")
                    .gauge();
            Gauge queued = registry.find("executor.queued")
                    .tag("name", "direct")
                    .tag("kind", "thread-pool")
                    .gauge();

            assertThat(completed).isNotNull();
            assertThat(completed.count()).isGreaterThanOrEqualTo(1.0d);
            assertThat(poolSize).isNotNull();
            assertThat(poolSize.value()).isGreaterThanOrEqualTo(0.0d);
            assertThat(queued).isNotNull();
            assertThat(queued.value()).isEqualTo(0.0d);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void monitorsScheduledExecutorCreatedByExecutorsFactory() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScheduledExecutorService delegate = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService monitored = null;
        try {
            monitored = ExecutorServiceMetrics.monitor(registry, delegate, "scheduled",
                    Collections.singleton(Tag.of("kind", "delegated-scheduled")));

            ScheduledFuture<Integer> scheduledTask = monitored.schedule(() -> 42, 0, TimeUnit.MILLISECONDS);

            assertThat(scheduledTask.get(5, TimeUnit.SECONDS)).isEqualTo(42);
            Counter scheduledOnce = registry.find("executor.scheduled.once")
                    .tag("name", "scheduled")
                    .tag("kind", "delegated-scheduled")
                    .counter();
            assertThat(scheduledOnce).isNotNull();
            assertThat(scheduledOnce.count()).isEqualTo(1.0d);
            Timer executionTimer = registry.find("executor")
                    .tag("name", "scheduled")
                    .tag("kind", "delegated-scheduled")
                    .timer();
            assertThat(executionTimer).isNotNull();
            assertThat(executionTimer.count()).isEqualTo(1L);

            FunctionCounter completed = registry.find("executor.completed")
                    .tag("name", "scheduled")
                    .tag("kind", "delegated-scheduled")
                    .functionCounter();
            if (completed != null) {
                assertThat(completed.count()).isGreaterThanOrEqualTo(1.0d);
            }
        } finally {
            shutdown(monitored != null ? monitored : delegate);
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
