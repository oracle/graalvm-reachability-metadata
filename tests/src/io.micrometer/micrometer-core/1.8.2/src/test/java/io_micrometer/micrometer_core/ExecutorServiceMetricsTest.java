/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorServiceMetricsTest {
    @Test
    void bindsMetricsForJdkDelegatedScheduledExecutorService() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService monitoredExecutor = null;
        try {
            monitoredExecutor = ExecutorServiceMetrics.monitor(registry, executor, "scheduled-delegate",
                    Collections.singleton(Tag.of("source", "test")));

            Gauge poolCore = registry.find("executor.pool.core")
                    .tag("name", "scheduled-delegate")
                    .tag("source", "test")
                    .gauge();
            Gauge poolMax = registry.find("executor.pool.max")
                    .tag("name", "scheduled-delegate")
                    .tag("source", "test")
                    .gauge();
            Gauge active = registry.find("executor.active")
                    .tag("name", "scheduled-delegate")
                    .tag("source", "test")
                    .gauge();

            assertThat(monitoredExecutor).isNotSameAs(executor);
            assertThat(poolCore).isNotNull();
            assertThat(poolMax).isNotNull();
            assertThat(active).isNotNull();
            assertThat(poolCore.value()).isEqualTo(1.0);
            assertThat(poolMax.value()).isEqualTo(Integer.MAX_VALUE);
            assertThat(active.value()).isZero();
        } finally {
            if (monitoredExecutor != null) {
                monitoredExecutor.shutdownNow();
            } else {
                executor.shutdownNow();
            }
            registry.close();
        }
    }
}
