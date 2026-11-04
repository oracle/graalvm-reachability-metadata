/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.MpscBlockingConsumerArrayQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' blocking multi-producer queue subsystem to hand jobs to a consumer.
public class MpscBlockingJobQueueTest {
    private static final int PRODUCER_COUNT = 2;
    private static final int JOBS_PER_PRODUCER = 2;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void takesJobsFromConcurrentProducersUntilTheQueueIsDrained() throws Exception {
        MpscBlockingConsumerArrayQueue<Job> jobs = new MpscBlockingConsumerArrayQueue<>(4);
        CountDownLatch workersReady = new CountDownLatch(PRODUCER_COUNT + 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(PRODUCER_COUNT + 1);
        List<Future<?>> producers = new ArrayList<>();
        try {
            Future<List<Job>> consumer = workers.submit(() -> {
                workersReady.countDown();
                awaitStart(start);
                List<Job> received = new ArrayList<>();
                for (int job = 0; job < PRODUCER_COUNT * JOBS_PER_PRODUCER; job++) {
                    received.add(jobs.take());
                }
                return received;
            });
            for (int producer = 1; producer <= PRODUCER_COUNT; producer++) {
                int producerId = producer;
                producers.add(workers.submit(() -> {
                    workersReady.countDown();
                    awaitStart(start);
                    for (int sequence = 1; sequence <= JOBS_PER_PRODUCER; sequence++) {
                        jobs.put(new Job(producerId, sequence));
                    }
                    return null;
                }));
            }

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> producer : producers) {
                producer.get(10, TimeUnit.SECONDS);
            }

            List<Job> received = consumer.get(10, TimeUnit.SECONDS);
            received.sort(Comparator.comparingInt(Job::producer).thenComparingInt(Job::sequence));
            List<Job> expected = List.of(
                    new Job(1, 1),
                    new Job(1, 2),
                    new Job(2, 1),
                    new Job(2, 2));
            assertEquals(expected, received);
            assertEquals(4, jobs.capacity());
            assertEquals(4, jobs.remainingCapacity());
            assertTrue(jobs.isEmpty());
        } finally {
            workers.shutdownNow();
        }
    }

    private static void awaitStart(CountDownLatch start) throws InterruptedException {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("The job queue did not start");
        }
    }

    private record Job(int producer, int sequence) {
    }
}
