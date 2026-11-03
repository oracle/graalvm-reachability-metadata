/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.MpmcUnboundedXaddArrayQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' unbounded multi-producer/multi-consumer queue subsystem to ingest application records.
public class MpmcUnboundedIngestQueueTest {
    private static final int PRODUCER_COUNT = 2;
    private static final int CONSUMER_COUNT = 2;
    private static final int RECORDS_PER_PRODUCER = 8;
    private static final int TOTAL_RECORDS = PRODUCER_COUNT * RECORDS_PER_PRODUCER;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void ingestsRecordsAcrossGrowingQueueChunks() throws Exception {
        MpmcUnboundedXaddArrayQueue<IngestRecord> records = new MpmcUnboundedXaddArrayQueue<>(4);
        CountDownLatch workersReady = new CountDownLatch(PRODUCER_COUNT + CONSUMER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger consumedCount = new AtomicInteger();
        List<IngestRecord> consumed = new CopyOnWriteArrayList<>();
        ExecutorService workers = Executors.newFixedThreadPool(PRODUCER_COUNT + CONSUMER_COUNT);
        List<Future<?>> producerCompletions = new ArrayList<>();
        List<Future<?>> consumerCompletions = new ArrayList<>();
        try {
            for (int producer = 1; producer <= PRODUCER_COUNT; producer++) {
                int producerId = producer;
                producerCompletions.add(workers.submit(() -> {
                    awaitStart(workersReady, start);
                    for (int sequence = 1; sequence <= RECORDS_PER_PRODUCER; sequence++) {
                        if (!records.offer(new IngestRecord(producerId, sequence))) {
                            throw new IllegalStateException("The unbounded ingest queue rejected a record");
                        }
                    }
                }));
            }
            for (int consumer = 0; consumer < CONSUMER_COUNT; consumer++) {
                consumerCompletions.add(workers.submit(() -> {
                    awaitStart(workersReady, start);
                    while (consumedCount.get() < TOTAL_RECORDS) {
                        IngestRecord record = records.poll();
                        if (record != null) {
                            consumed.add(record);
                            consumedCount.incrementAndGet();
                        } else {
                            Thread.onSpinWait();
                        }
                    }
                }));
            }

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> completion : producerCompletions) {
                completion.get(10, TimeUnit.SECONDS);
            }
            for (Future<?> completion : consumerCompletions) {
                completion.get(10, TimeUnit.SECONDS);
            }

            assertEquals(TOTAL_RECORDS, consumed.size());
            assertEquals(TOTAL_RECORDS, consumed.stream().distinct().count());
            assertTrue(consumed.contains(new IngestRecord(1, 1)));
            assertTrue(consumed.contains(new IngestRecord(PRODUCER_COUNT, RECORDS_PER_PRODUCER)));
            assertEquals(TOTAL_RECORDS, records.currentProducerIndex());
            assertEquals(TOTAL_RECORDS, records.currentConsumerIndex());
            assertEquals(0, records.size());
            assertTrue(records.isEmpty());
        } finally {
            workers.shutdownNow();
        }
    }

    private static void awaitStart(CountDownLatch workersReady, CountDownLatch start) {
        workersReady.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The ingest workers did not start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The ingest workers were interrupted", interrupted);
        }
    }

    private record IngestRecord(int producer, int sequence) {
    }
}
