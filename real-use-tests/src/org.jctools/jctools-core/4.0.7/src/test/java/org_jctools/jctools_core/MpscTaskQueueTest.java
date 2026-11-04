/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.MpscArrayQueue;
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

/// Exercises JCTools' bounded multi-producer queue subsystem to dispatch work from two producers.
public class MpscTaskQueueTest {
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void dispatchesTasksFromMultipleProducersInTheSubmittedQueue() throws Exception {
        MpscArrayQueue<String> tasks = new MpscArrayQueue<>(8);
        ExecutorService producers = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> billingTasks = producers.submit(() -> produce(tasks, "billing", 2));
            Future<Integer> shippingTasks = producers.submit(() -> produce(tasks, "shipping", 2));

            assertEquals(2, billingTasks.get(10, TimeUnit.SECONDS));
            assertEquals(2, shippingTasks.get(10, TimeUnit.SECONDS));
            assertEquals(8, tasks.capacity());
            assertEquals(4, tasks.size());
            assertTrue(tasks.contains("billing-1"));
            assertFalse(tasks.isEmpty());

            List<String> dispatched = new ArrayList<>();
            String task;
            while ((task = tasks.poll()) != null) {
                dispatched.add(task);
            }

            assertEquals(List.of("billing-1", "billing-2", "shipping-1", "shipping-2"),
                    dispatched.stream().sorted().toList());
            assertTrue(tasks.isEmpty());
            assertEquals(0, tasks.size());
        } finally {
            producers.shutdownNow();
        }
    }

    private static int produce(MpscArrayQueue<String> tasks, String category, int count) {
        for (int taskNumber = 1; taskNumber <= count; taskNumber++) {
            if (!tasks.offer(category + "-" + taskNumber)) {
                throw new IllegalStateException("The dispatch queue is full");
            }
        }
        return count;
    }
}
