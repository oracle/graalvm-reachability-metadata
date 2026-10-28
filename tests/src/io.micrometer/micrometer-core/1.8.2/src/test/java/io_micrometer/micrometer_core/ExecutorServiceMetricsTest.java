/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorServiceMetricsTest {

    @Test
    void unwrapsDelegatingScheduledExecutorToReportPoolMetrics() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();
        ScheduledExecutorService scheduler = ExecutorServiceMetrics.monitor(registry,
                Executors.newSingleThreadScheduledExecutor(), "scheduler");
        try {
            ScheduledFuture<String> result = scheduler.schedule(() -> "done", 1, TimeUnit.MILLISECONDS);
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("done");

            assertThat(registry.get("executor.pool.core").tag("name", "scheduler").gauge().value()).isEqualTo(1.0);
            assertThat(registry.get("executor.pool.size").tag("name", "scheduler").gauge().value()).isEqualTo(1.0);
            assertThat(registry.get("executor.completed").tag("name", "scheduler").functionCounter().count())
                    .isEqualTo(1.0);
            assertThat(registry.get("executor.scheduled.once").tag("name", "scheduler").counter().count())
                    .isEqualTo(1.0);
        } finally {
            scheduler.shutdownNow();
        }
    }
}
