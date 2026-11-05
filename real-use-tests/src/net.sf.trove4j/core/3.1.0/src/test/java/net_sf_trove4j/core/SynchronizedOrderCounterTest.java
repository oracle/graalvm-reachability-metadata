/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.TCollections;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Trove's synchronized-map wrapper subsystem to keep shared order counters consistent.
public class SynchronizedOrderCounterTest {
    @Test
    @Timeout(60)
    public void aggregatesOrderEventsFromConcurrentWorkers() throws Exception {
        TObjectIntMap<String> orderCounters =
                TCollections.synchronizedMap(new TObjectIntHashMap<>());
        orderCounters.put("orders", 0);
        orderCounters.put("returns", 0);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<?> firstWorker = workers.submit(
                () -> recordOrderEvents(orderCounters, ready, start, finished, 150));
        Future<?> secondWorker = workers.submit(
                () -> recordOrderEvents(orderCounters, ready, start, finished, 150));

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(finished.await(10, TimeUnit.SECONDS));
        firstWorker.get();
        secondWorker.get();
        workers.shutdown();
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(2, orderCounters.size());
        assertEquals(300, orderCounters.get("orders"));
        assertEquals(300, orderCounters.get("returns"));
    }

    private static void recordOrderEvents(
            TObjectIntMap<String> orderCounters,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch finished,
            int eventCount) {
        ready.countDown();
        try {
            start.await();
            for (int event = 0; event < eventCount; event++) {
                orderCounters.adjustOrPutValue("orders", 1, 1);
                orderCounters.adjustOrPutValue("returns", 1, 1);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker interrupted", exception);
        } finally {
            finished.countDown();
        }
    }
}
