/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.NamingThreadFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Names application worker threads with Resilience4j's thread-factory subsystem.
 *
 * A checkout service creates a bounded worker pool and expects its threads to carry a stable
 * service-specific prefix, making worker activity identifiable in operational diagnostics.
 */
class NamedWorkerThreadsTest {

    @Test
    void givesCheckoutWorkersStableServiceNames() throws InterruptedException {
        NamingThreadFactory threadFactory = new NamingThreadFactory("checkout-worker");
        ExecutorService workers = Executors.newFixedThreadPool(2, threadFactory);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        List<String> workerNames = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int task = 0; task < 2; task++) {
                workers.submit(() -> {
                    workerNames.add(Thread.currentThread().getName());
                    workersReady.countDown();
                    await(releaseWorkers);
                });
            }

            assertThat(workersReady.await(10, TimeUnit.SECONDS)).isTrue();
            releaseWorkers.countDown();
            workers.shutdown();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(workerNames).containsExactlyInAnyOrder(
                    "checkout-worker-1", "checkout-worker-2");
        } finally {
            releaseWorkers.countDown();
            workers.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker release timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("worker was interrupted", interrupted);
        }
    }
}
