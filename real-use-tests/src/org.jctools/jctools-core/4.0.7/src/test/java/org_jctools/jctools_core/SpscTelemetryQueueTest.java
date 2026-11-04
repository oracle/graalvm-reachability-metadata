/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.SpscGrowableArrayQueue;
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

/// Exercises JCTools' single-producer/single-consumer growable queue subsystem to transfer telemetry samples.
public class SpscTelemetryQueueTest {
    private static final int SAMPLE_COUNT = 20;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void transfersSamplesInOrderAsTheQueueGrows() throws Exception {
        SpscGrowableArrayQueue<Sample> samples = new SpscGrowableArrayQueue<>(16, 32);
        CountDownLatch producerStarted = new CountDownLatch(1);
        CountDownLatch consumerStarted = new CountDownLatch(1);
        CountDownLatch producerFinished = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        List<Sample> received = new ArrayList<>();
        try {
            Future<?> producer = workers.submit(() -> {
                producerStarted.countDown();
                if (!await(consumerStarted)) {
                    throw new IllegalStateException("The telemetry consumer did not start");
                }
                for (int sequence = 1; sequence <= SAMPLE_COUNT; sequence++) {
                    if (!samples.offer(new Sample(sequence, sequence * 10))) {
                        throw new IllegalStateException("The telemetry queue is full");
                    }
                }
                producerFinished.countDown();
            });
            Future<?> consumer = workers.submit(() -> {
                if (!await(producerStarted)) {
                    throw new IllegalStateException("The telemetry producer did not start");
                }
                consumerStarted.countDown();
                while (!await(producerFinished, 1, TimeUnit.MILLISECONDS) || !samples.isEmpty()) {
                    Sample sample = samples.poll();
                    if (sample != null) {
                        received.add(sample);
                    }
                }
                Sample sample;
                while ((sample = samples.poll()) != null) {
                    received.add(sample);
                }
            });

            producer.get(10, TimeUnit.SECONDS);
            consumer.get(10, TimeUnit.SECONDS);

            assertEquals(SAMPLE_COUNT, received.size());
            for (int sequence = 1; sequence <= SAMPLE_COUNT; sequence++) {
                assertEquals(new Sample(sequence, sequence * 10), received.get(sequence - 1));
            }
            assertEquals(32, samples.capacity());
            assertTrue(samples.isEmpty());
        } finally {
            workers.shutdownNow();
        }
    }

    private static boolean await(CountDownLatch latch) {
        return await(latch, 10, TimeUnit.SECONDS);
    }

    private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record Sample(int sequence, int value) {
    }
}
