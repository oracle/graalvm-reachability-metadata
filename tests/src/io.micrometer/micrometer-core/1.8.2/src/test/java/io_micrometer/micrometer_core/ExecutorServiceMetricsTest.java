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
    void bindsMetricsForDelegatedScheduledExecutorService() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            ExecutorServiceMetrics metrics = new ExecutorServiceMetrics(
                    executor, "delegated", Tags.of("kind", "scheduled"));

            metrics.bindTo(registry);

            Gauge poolSize = registry.get("executor.pool.size")
                    .tag("name", "delegated")
                    .tag("kind", "scheduled")
                    .gauge();
            Gauge corePoolSize = registry.get("executor.pool.core")
                    .tag("name", "delegated")
                    .tag("kind", "scheduled")
                    .gauge();
            Gauge maxPoolSize = registry.get("executor.pool.max")
                    .tag("name", "delegated")
                    .tag("kind", "scheduled")
                    .gauge();

            assertThat(poolSize.value()).isNotNaN();
            assertThat(corePoolSize.value()).isEqualTo(1.0);
            assertThat(maxPoolSize.value()).isPositive();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            registry.close();
        }
    }
}
