/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class SimpleTimeLimiterTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void proxyDispatchesInterruptibleInterfaceMethodThroughTimeLimiter() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            BlockingQueue<String> target = new ArrayBlockingQueue<>(1);
            SimpleTimeLimiter timeLimiter = SimpleTimeLimiter.create(executor);
            BlockingQueue<String> proxy =
                    timeLimiter.newProxy(target, BlockingQueue.class, 5, TimeUnit.SECONDS);

            proxy.put("delivered");

            assertEquals("delivered", target.poll(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
