/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.MpscUnboundedArrayQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' unbounded multi-producer queue subsystem to collect work from application workers.
public class MpscUnboundedWorkQueueTest {
    private static final int PRODUCER_COUNT = 3;
    private static final int ITEMS_PER_PRODUCER = 5;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void collectsAllWorkAfterTheQueueGrowsBeyondItsInitialBuffer() throws Exception {
        MpscUnboundedArrayQueue<WorkItem> workQueue = new MpscUnboundedArrayQueue<>(2);
        ExecutorService producers = Executors.newFixedThreadPool(PRODUCER_COUNT);
        List<Future<?>> completions = new ArrayList<>();
        try {
            for (int producer = 1; producer <= PRODUCER_COUNT; producer++) {
                int producerId = producer;
                completions.add(producers.submit(() -> produce(workQueue, producerId)));
            }

            for (Future<?> completion : completions) {
                completion.get(10, TimeUnit.SECONDS);
            }

            assertEquals(PRODUCER_COUNT * ITEMS_PER_PRODUCER, workQueue.size());
            assertFalse(workQueue.isEmpty());

            List<WorkItem> collected = new ArrayList<>();
            WorkItem workItem;
            while ((workItem = workQueue.poll()) != null) {
                collected.add(workItem);
            }
            collected.sort(WorkItem::compareTo);

            List<WorkItem> expected = new ArrayList<>();
            for (int producer = 1; producer <= PRODUCER_COUNT; producer++) {
                for (int sequence = 1; sequence <= ITEMS_PER_PRODUCER; sequence++) {
                    expected.add(new WorkItem(producer, sequence));
                }
            }
            assertEquals(expected, collected);
            assertTrue(workQueue.isEmpty());
            assertEquals(0, workQueue.size());
        } finally {
            producers.shutdownNow();
        }
    }

    private static void produce(MpscUnboundedArrayQueue<WorkItem> workQueue, int producer) {
        for (int sequence = 1; sequence <= ITEMS_PER_PRODUCER; sequence++) {
            if (!workQueue.offer(new WorkItem(producer, sequence))) {
                throw new IllegalStateException("The unbounded work queue rejected an item");
            }
        }
    }

    private record WorkItem(int producer, int sequence) implements Comparable<WorkItem> {
        @Override
        public int compareTo(WorkItem other) {
            int producerComparison = Integer.compare(producer, other.producer);
            return producerComparison != 0
                    ? producerComparison
                    : Integer.compare(sequence, other.sequence);
        }
    }
}
