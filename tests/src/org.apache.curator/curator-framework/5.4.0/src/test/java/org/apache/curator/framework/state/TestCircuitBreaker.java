/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.state;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.RetryForever;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.retry.RetryUntilElapsed;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"FieldMayBeFinal", "NullableProblems"})
public class TestCircuitBreaker {
    private static Duration[] lastDelay = new Duration[]{Duration.ZERO};
    private static ScheduledThreadPoolExecutor service = new ScheduledThreadPoolExecutor(1) {
        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            lastDelay[0] = Duration.of(unit.toNanos(delay), ChronoUnit.NANOS);
            command.run();
            return null;
        }
    };

    @AfterAll
    public static void tearDown() {
        service.shutdownNow();
    }

    @Test
    public void testBasic() {
        final int retryQty = 1;
        final Duration delay = Duration.ofSeconds(10);
        CircuitBreaker circuitBreaker = CircuitBreaker.build(new RetryNTimes(retryQty, (int) delay.toMillis()), service);
        AtomicInteger counter = new AtomicInteger(0);
        assertTrue(circuitBreaker.tryToOpen(counter::incrementAndGet));
        assertEquals(lastDelay[0], delay);
        assertFalse(circuitBreaker.tryToOpen(counter::incrementAndGet));
        assertEquals(circuitBreaker.getRetryCount(), 1);
        assertEquals(counter.get(), 1);
        assertFalse(circuitBreaker.tryToRetry(counter::incrementAndGet));
        assertEquals(circuitBreaker.getRetryCount(), 1);
        assertEquals(counter.get(), 1);
        assertTrue(circuitBreaker.close());
        assertEquals(circuitBreaker.getRetryCount(), 0);
        assertFalse(circuitBreaker.close());
    }

    @Test
    public void testVariousOpenRetryFails() {
        CircuitBreaker circuitBreaker = CircuitBreaker.build(new RetryForever(1), service);
        assertFalse(circuitBreaker.tryToRetry(() -> {
        }));
        assertTrue(circuitBreaker.tryToOpen(() -> {
        }));
        assertFalse(circuitBreaker.tryToOpen(() -> {
        }));
        assertTrue(circuitBreaker.close());
        assertFalse(circuitBreaker.close());
    }

    @Test
    public void testWithRetryUntilElapsed() {
        RetryPolicy retryPolicy = new RetryUntilElapsed(10000, 10000);
        CircuitBreaker circuitBreaker = CircuitBreaker.build(retryPolicy, service);
        assertTrue(circuitBreaker.tryToOpen(() -> {
        }));
        assertEquals(lastDelay[0], Duration.ofMillis(10000));
    }
}
