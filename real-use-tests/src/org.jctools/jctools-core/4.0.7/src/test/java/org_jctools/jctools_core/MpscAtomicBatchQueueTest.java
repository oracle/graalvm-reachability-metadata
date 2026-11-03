/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.atomic.MpscAtomicArrayQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' atomic multi-producer queue subsystem to batch application work.
public class MpscAtomicBatchQueueTest {
    private static final int PRODUCER_COUNT = 2;
    private static final int ITEMS_PER_PRODUCER = 3;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void batchesWorkFromConcurrentProducersAndDrainsIt() throws Exception {
        MpscAtomicArrayQueue<WorkItem> workQueue = new MpscAtomicArrayQueue<>(8);
        ExecutorService producers = Executors.newFixedThreadPool(PRODUCER_COUNT);
        List<Future<Integer>> completions = new ArrayList<>();
        try {
            for (int producer = 1; producer <= PRODUCER_COUNT; producer++) {
                int producerId = producer;
                completions.add(producers.submit(() -> {
                    AtomicInteger sequence = new AtomicInteger(1);
                    return workQueue.fill(
                            () -> new WorkItem(producerId, sequence.getAndIncrement()),
                            ITEMS_PER_PRODUCER);
                }));
            }

            for (Future<Integer> completion : completions) {
                assertEquals(ITEMS_PER_PRODUCER, completion.get(10, TimeUnit.SECONDS));
            }

            int expectedSize = PRODUCER_COUNT * ITEMS_PER_PRODUCER;
            assertEquals(expectedSize, workQueue.size());
            assertEquals(8, workQueue.capacity());

            List<WorkItem> dispatched = new ArrayList<>();
            assertEquals(expectedSize, workQueue.drain(dispatched::add, expectedSize));
            dispatched.sort(Comparator.comparingInt(WorkItem::producer)
                    .thenComparingInt(WorkItem::sequence));

            List<WorkItem> expected = new ArrayList<>();
            for (int producer = 1; producer <= PRODUCER_COUNT; producer++) {
                for (int sequence = 1; sequence <= ITEMS_PER_PRODUCER; sequence++) {
                    expected.add(new WorkItem(producer, sequence));
                }
            }
            assertEquals(expected, dispatched);
            assertTrue(workQueue.isEmpty());
        } finally {
            producers.shutdownNow();
        }
    }

    private record WorkItem(int producer, int sequence) {
    }
}
