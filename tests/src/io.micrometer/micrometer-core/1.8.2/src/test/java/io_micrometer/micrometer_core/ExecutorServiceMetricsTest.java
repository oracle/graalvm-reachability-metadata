/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorServiceMetricsTest {
    @Test
    void bindsMetricsForSingleThreadScheduledExecutorCreatedByExecutors() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        try {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            ExecutorServiceMetrics metrics = new ExecutorServiceMetrics(
                    executorService,
                    "single-threaded",
                    Collections.singletonList(Tag.of("scenario", "delegated-scheduled-executor"))
            );

            metrics.bindTo(registry);

            FunctionCounter completed = registry.get("executor.completed")
                    .tag("name", "single-threaded")
                    .tag("scenario", "delegated-scheduled-executor")
                    .functionCounter();
            Gauge active = registry.get("executor.active")
                    .tag("name", "single-threaded")
                    .tag("scenario", "delegated-scheduled-executor")
                    .gauge();
            Gauge queued = registry.get("executor.queued")
                    .tag("name", "single-threaded")
                    .tag("scenario", "delegated-scheduled-executor")
                    .gauge();
            Gauge queueRemaining = registry.get("executor.queue.remaining")
                    .tag("name", "single-threaded")
                    .tag("scenario", "delegated-scheduled-executor")
                    .gauge();
            Gauge corePoolSize = registry.get("executor.pool.core")
                    .tag("name", "single-threaded")
                    .tag("scenario", "delegated-scheduled-executor")
                    .gauge();
            Gauge maximumPoolSize = registry.get("executor.pool.max")
                    .tag("name", "single-threaded")
                    .tag("scenario", "delegated-scheduled-executor")
                    .gauge();

            assertThat(completed.count()).isZero();
            assertThat(active.value()).isZero();
            assertThat(queued.value()).isZero();
            assertThat(queueRemaining.value()).isGreaterThan(0.0);
            assertThat(corePoolSize.value()).isEqualTo(1.0);
            assertThat(maximumPoolSize.value()).isGreaterThanOrEqualTo(1.0);
        } finally {
            executorService.shutdownNow();
        }
    }
}
