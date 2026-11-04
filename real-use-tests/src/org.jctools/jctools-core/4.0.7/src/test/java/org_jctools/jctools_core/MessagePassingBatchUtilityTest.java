/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.MessagePassingQueueUtil;
import org.jctools.queues.SpscArrayQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' message-passing queue utility subsystem to batch application records.
public class MessagePassingBatchUtilityTest {
    private static final int QUEUE_CAPACITY = 8;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void fillsAQueueAndDrainsRecordsInApplicationBatches() {
        SpscArrayQueue<BatchRecord> records = new SpscArrayQueue<>(QUEUE_CAPACITY);
        AtomicInteger nextSequence = new AtomicInteger(1);

        int filled = MessagePassingQueueUtil.fillBounded(records,
                () -> new BatchRecord(nextSequence.getAndIncrement(), "record"));

        List<BatchRecord> consumed = new ArrayList<>();
        int firstBatch = MessagePassingQueueUtil.drain(records, consumed::add, 3);
        int remaining = MessagePassingQueueUtil.drain(records, consumed::add);

        assertEquals(QUEUE_CAPACITY, filled);
        assertEquals(3, firstBatch);
        assertEquals(QUEUE_CAPACITY - firstBatch, remaining);
        assertEquals(QUEUE_CAPACITY, consumed.size());
        assertEquals(QUEUE_CAPACITY + 1, nextSequence.get());
        assertEquals(new BatchRecord(1, "record"), consumed.get(0));
        assertEquals(new BatchRecord(3, "record"), consumed.get(2));
        assertEquals(new BatchRecord(8, "record"), consumed.get(7));
        assertTrue(records.isEmpty());
    }

    private record BatchRecord(int sequence, String kind) {
    }
}
