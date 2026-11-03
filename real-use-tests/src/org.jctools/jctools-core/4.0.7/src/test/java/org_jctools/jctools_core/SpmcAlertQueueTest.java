/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.SpmcArrayQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' single-producer/multiple-consumer queue subsystem to fan out alerts to workers.
public class SpmcAlertQueueTest {
    private static final int CONSUMER_COUNT = 2;
    private static final int ALERT_COUNT = 8;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void deliversEveryAlertToOneOfTheConcurrentWorkers() throws Exception {
        SpmcArrayQueue<Alert> alerts = new SpmcArrayQueue<>(16);
        CountDownLatch workersReady = new CountDownLatch(CONSUMER_COUNT + 1);
        CountDownLatch start = new CountDownLatch(1);
        List<Alert> handled = new CopyOnWriteArrayList<>();
        ExecutorService workers = Executors.newFixedThreadPool(CONSUMER_COUNT + 1);
        List<Future<?>> consumers = new ArrayList<>();
        try {
            Future<?> producer = workers.submit(() -> {
                awaitStart(workersReady, start);
                for (int sequence = 1; sequence <= ALERT_COUNT; sequence++) {
                    if (!alerts.offer(new Alert(sequence, "alert-" + sequence))) {
                        throw new IllegalStateException("The alert queue is full");
                    }
                }
                for (int consumer = 0; consumer < CONSUMER_COUNT; consumer++) {
                    if (!alerts.offer(Alert.sentinel())) {
                        throw new IllegalStateException("The alert queue rejected a stop marker");
                    }
                }
            });
            for (int consumer = 0; consumer < CONSUMER_COUNT; consumer++) {
                consumers.add(workers.submit(() -> {
                    awaitStart(workersReady, start);
                    Alert alert;
                    while ((alert = alerts.poll()) == null) {
                        Thread.onSpinWait();
                    }
                    while (!alert.stop()) {
                        handled.add(alert);
                        do {
                            alert = alerts.poll();
                            if (alert == null) {
                                Thread.onSpinWait();
                            }
                        } while (alert == null);
                    }
                }));
            }

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            producer.get(10, TimeUnit.SECONDS);
            for (Future<?> consumer : consumers) {
                consumer.get(10, TimeUnit.SECONDS);
            }

            List<Alert> expected = new ArrayList<>();
            for (int sequence = 1; sequence <= ALERT_COUNT; sequence++) {
                expected.add(new Alert(sequence, "alert-" + sequence));
            }
            assertEquals(expected.stream().sorted(Comparator.comparingInt(Alert::sequence)).toList(),
                    handled.stream().sorted(Comparator.comparingInt(Alert::sequence)).toList());
            assertEquals(16, alerts.capacity());
            assertTrue(alerts.isEmpty());
        } finally {
            workers.shutdownNow();
        }
    }

    private static void awaitStart(CountDownLatch workersReady, CountDownLatch start) {
        workersReady.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The alert queue did not start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The alert queue was interrupted", interrupted);
        }
    }

    private record Alert(int sequence, String message, boolean stop) {
        private Alert(int sequence, String message) {
            this(sequence, message, false);
        }

        private static Alert sentinel() {
            return new Alert(0, "stop", true);
        }
    }
}
