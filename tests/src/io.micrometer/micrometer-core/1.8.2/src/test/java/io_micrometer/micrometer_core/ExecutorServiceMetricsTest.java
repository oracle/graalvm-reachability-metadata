/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorServiceMetricsTest {
    @Test
    void bindsMetricsForDelegatedScheduledExecutorService() throws InterruptedException {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Tags tags = Tags.of("scenario", "delegated");

        try {
            new ExecutorServiceMetrics(executorService, "scheduled", tags).bindTo(registry);

            Gauge corePoolSize = registry.get("executor.pool.core")
                    .tags(tags.and("name", "scheduled"))
                    .gauge();
            Gauge maximumPoolSize = registry.get("executor.pool.max")
                    .tags(tags.and("name", "scheduled"))
                    .gauge();
            Gauge queuedTasks = registry.get("executor.queued")
                    .tags(tags.and("name", "scheduled"))
                    .gauge();

            assertThat(corePoolSize.value()).isEqualTo(1.0);
            assertThat(maximumPoolSize.value()).isGreaterThanOrEqualTo(1.0);
            assertThat(queuedTasks.value()).isEqualTo(0.0);
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
