/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.MpmcArrayQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' bounded multi-producer/multi-consumer queue subsystem to distribute work items.
public class MpmcWorkQueueTest {
    private static final int PRODUCER_COUNT = 2;
    private static final int CONSUMER_COUNT = 2;
    private static final int ITEMS_PER_PRODUCER = 4;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void distributesItemsAmongConcurrentWorkers() throws Exception {
        MpmcArrayQueue<WorkItem> workQueue = new MpmcArrayQueue<>(16);
        CountDownLatch workersReady = new CountDownLatch(PRODUCER_COUNT + CONSUMER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(PRODUCER_COUNT + CONSUMER_COUNT);
        List<WorkItem> processed = new CopyOnWriteArrayList<>();
        List<Future<?>> producerCompletions = new ArrayList<>();
        List<Future<?>> consumerCompletions = new ArrayList<>();
        try {
            for (int producer = 1; producer <= PRODUCER_COUNT; producer++) {
                int producerId = producer;
                producerCompletions.add(workers.submit(() -> {
                    awaitStart(workersReady, start);
                    for (int item = 1; item <= ITEMS_PER_PRODUCER; item++) {
                        WorkItem workItem = new WorkItem(producerId, item, false);
                        if (!workQueue.offer(workItem)) {
                            throw new IllegalStateException("The work queue is full");
                        }
                    }
                }));
            }
            for (int consumer = 0; consumer < CONSUMER_COUNT; consumer++) {
                consumerCompletions.add(workers.submit(() -> {
                    awaitStart(workersReady, start);
                    WorkItem workItem;
                    while ((workItem = workQueue.poll()) == null || !workItem.poison()) {
                        if (workItem != null) {
                            processed.add(workItem);
                        }
                    }
                }));
            }

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> completion : producerCompletions) {
                completion.get(10, TimeUnit.SECONDS);
            }
            assertEquals(16, workQueue.capacity());
            for (int consumer = 0; consumer < CONSUMER_COUNT; consumer++) {
                assertTrue(workQueue.offer(new WorkItem(0, 0, true)));
            }
            for (Future<?> completion : consumerCompletions) {
                completion.get(10, TimeUnit.SECONDS);
            }

            assertEquals(PRODUCER_COUNT * ITEMS_PER_PRODUCER, processed.size());
            assertEquals(PRODUCER_COUNT * ITEMS_PER_PRODUCER, processed.stream().distinct().count());
            assertTrue(processed.contains(new WorkItem(1, 1, false)));
            assertTrue(processed.contains(new WorkItem(2, ITEMS_PER_PRODUCER, false)));
            assertTrue(workQueue.isEmpty());
        } finally {
            workers.shutdownNow();
        }
    }

    private static void awaitStart(CountDownLatch workersReady, CountDownLatch start) {
        workersReady.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The work queue did not start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The work queue was interrupted", interrupted);
        }
    }

    private record WorkItem(int producer, int sequence, boolean poison) {
    }
}
