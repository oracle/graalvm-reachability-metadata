/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.counters.Counter;
import org.jctools.counters.CountersFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' striped counter subsystem to aggregate events from application workers.
public class StripedEventCounterTest {
    private static final int WORKER_COUNT = 4;
    private static final int EVENTS_PER_WORKER = 250;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void aggregatesWorkerEventsAndPublishesThenResetsTheCount() throws Exception {
        Counter processedEvents = CountersFactory.createFixedSizeStripedCounter(WORKER_COUNT);
        CountDownLatch workersReady = new CountDownLatch(WORKER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(WORKER_COUNT);
        List<Future<?>> completions = new ArrayList<>();
        try {
            for (int worker = 0; worker < WORKER_COUNT; worker++) {
                completions.add(workers.submit(() -> {
                    recordEvents(processedEvents, workersReady, start);
                    return null;
                }));
            }

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> completion : completions) {
                completion.get(10, TimeUnit.SECONDS);
            }

            assertEquals((long) WORKER_COUNT * EVENTS_PER_WORKER, processedEvents.get());
            assertEquals((long) WORKER_COUNT * EVENTS_PER_WORKER, processedEvents.getAndReset());
            assertEquals(0L, processedEvents.get());

            processedEvents.inc(3);
            assertEquals(3L, processedEvents.getAndReset());
            assertEquals(0L, processedEvents.get());
        } finally {
            workers.shutdownNow();
        }
    }

    private static void recordEvents(Counter processedEvents, CountDownLatch workersReady,
            CountDownLatch start) throws InterruptedException {
        workersReady.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("The event counter did not start");
        }
        processedEvents.inc(EVENTS_PER_WORKER);
    }
}
