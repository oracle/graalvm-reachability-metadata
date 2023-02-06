/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client.utils;

import org.apache.curator.utils.CloseableScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("resource")
public class TestCloseableScheduledExecutorService {
    private static final int QTY = 10;
    private static final int DELAY_MS = 100;

    private volatile ScheduledExecutorService executorService;

    @BeforeEach
    public void setup() {
        executorService = Executors.newScheduledThreadPool(QTY * 2);
    }

    @AfterEach
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void testCloseableScheduleWithFixedDelay() throws InterruptedException {
        CloseableScheduledExecutorService service = new CloseableScheduledExecutorService(executorService);
        final CountDownLatch latch = new CountDownLatch(QTY);
        service.scheduleWithFixedDelay(latch::countDown, DELAY_MS, DELAY_MS, TimeUnit.MILLISECONDS);
        assertTrue(latch.await((QTY * 2) * DELAY_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testCloseableScheduleWithFixedDelayAndAdditionalTasks() throws InterruptedException {
        final AtomicInteger outerCounter = new AtomicInteger(0);
        Runnable command = outerCounter::incrementAndGet;
        executorService.scheduleWithFixedDelay(command, DELAY_MS, DELAY_MS, TimeUnit.MILLISECONDS);
        CloseableScheduledExecutorService service = new CloseableScheduledExecutorService(executorService);
        final AtomicInteger innerCounter = new AtomicInteger(0);
        service.scheduleWithFixedDelay(innerCounter::incrementAndGet, DELAY_MS, DELAY_MS, TimeUnit.MILLISECONDS);
        Thread.sleep(DELAY_MS * 4);
        service.close();
        Thread.sleep(DELAY_MS * 2);
        int innerValue = innerCounter.get();
        assertTrue(innerValue > 0);
        int value = outerCounter.get();
        Thread.sleep(DELAY_MS * 2);
        int newValue = outerCounter.get();
        assertTrue(newValue > value);
        assertEquals(innerValue, innerCounter.get());
        value = newValue;
        Thread.sleep(DELAY_MS * 2);
        newValue = outerCounter.get();
        assertTrue(newValue > value);
        assertEquals(innerValue, innerCounter.get());
    }
}
