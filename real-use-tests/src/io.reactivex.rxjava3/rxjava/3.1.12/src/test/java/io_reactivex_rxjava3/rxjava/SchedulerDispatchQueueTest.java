/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scheduler workers dispatch an application's order-processing jobs in sequence. */
public class SchedulerDispatchQueueTest {
    @Test
    @Timeout(60)
    public void workerRunsOrderJobsOnTheApplicationExecutor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Scheduler scheduler = Schedulers.from(executor);
        Scheduler.Worker worker = scheduler.createWorker();
        List<String> completedJobs = new ArrayList<>();
        CountDownLatch jobsCompleted = new CountDownLatch(3);

        try {
            worker.schedule(job(completedJobs, jobsCompleted, "validated"));
            worker.schedule(job(completedJobs, jobsCompleted, "reserved"));
            worker.schedule(job(completedJobs, jobsCompleted, "confirmed"));

            assertTrue(jobsCompleted.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("validated", "reserved", "confirmed"), completedJobs);
        } finally {
            worker.dispose();
            executor.shutdownNow();
        }
    }

    private static Runnable job(List<String> completedJobs, CountDownLatch jobsCompleted,
            String name) {
        return () -> {
            completedJobs.add(name);
            jobsCompleted.countDown();
        };
    }
}
