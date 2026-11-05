/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MultiReaderList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Eclipse Collections' multi-reader list subsystem to serve a dispatch queue.
public class MultiReaderDispatchQueueTest {
    @Test
    @Timeout(60)
    public void letsReadersFinishBeforePublishingTheNextDispatchBatch() throws Exception {
        MultiReaderList<String> dispatchQueue = Lists.multiReader.with("order-101", "order-102");
        CountDownLatch readerEntered = new CountDownLatch(1);
        CountDownLatch writerAttempted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> writer = executor.submit(() -> {
                await(readerEntered);
                writerAttempted.countDown();
                dispatchQueue.withWriteLockAndDelegate(queue -> {
                    queue.add("order-103");
                    queue.add("order-104");
                });
            });

            dispatchQueue.withReadLockAndDelegate(queue -> {
                readerEntered.countDown();
                await(writerAttempted);
                assertIterableEquals(List.of("order-101", "order-102"), queue);
            });

            writer.get(10, TimeUnit.SECONDS);

            List<String> publishedBatch = new ArrayList<>();
            dispatchQueue.withReadLockAndDelegate(publishedBatch::addAll);
            assertEquals(
                    List.of("order-101", "order-102", "order-103", "order-104"), publishedBatch);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the dispatch queue coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "Interrupted while coordinating the dispatch queue", exception);
        }
    }
}
