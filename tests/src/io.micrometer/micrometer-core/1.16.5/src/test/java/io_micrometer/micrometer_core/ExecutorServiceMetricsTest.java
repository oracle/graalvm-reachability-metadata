/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorServiceMetricsTest {

    @Test
    void unwrapsSingleThreadExecutorToReportPoolMetrics() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            ExecutorService executor = ExecutorServiceMetrics.monitor(registry,
                    Executors.newSingleThreadExecutor(), "worker");
            try {
                assertThat(executor.submit(() -> "done").get(30, TimeUnit.SECONDS)).isEqualTo("done");
                assertThat(registry.get("executor.pool.core").tag("name", "worker").gauge().value()).isEqualTo(1.0);
                assertThat(registry.get("executor.pool.size").tag("name", "worker").gauge().value()).isEqualTo(1.0);
                assertThat(registry.get("executor").tag("name", "worker").timer().count()).isEqualTo(1);
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void reportsActiveVirtualThreadTasks() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            ExecutorService executor = ExecutorServiceMetrics.monitor(registry,
                    Executors.newVirtualThreadPerTaskExecutor(), "virtual");
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try {
                Future<Boolean> result = executor.submit(() -> {
                    started.countDown();
                    return release.await(30, TimeUnit.SECONDS);
                });
                assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
                assertThat(registry.get("executor.active").tag("name", "virtual").gauge().value()).isEqualTo(1.0);
                release.countDown();
                assertThat(result.get(30, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            registry.close();
        }
    }
}
