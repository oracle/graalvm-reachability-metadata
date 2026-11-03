/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's asynchronous execution subsystem while an order service retries a transient failure.
public class AsyncOrderProcessingTest {

    @Test
    @Timeout(60)
    void eventuallyReservesAnOrderAfterTransientFailures() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                    .withMaxRetries(2)
                    .build();
            FailsafeExecutor<String> failsafe = Failsafe.with(retryPolicy).with(workers);
            OrderService service = new OrderService();

            CompletableFuture<String> reservation = failsafe.getAsync(
                    () -> service.reserve("order-1042"));

            assertThat(reservation.get(10, TimeUnit.SECONDS)).isEqualTo("reserved:order-1042");
            assertThat(service.attempts()).isEqualTo(3);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class OrderService {
        private final AtomicInteger attempts = new AtomicInteger();

        String reserve(String orderId) throws IOException {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new IOException("inventory service temporarily unavailable");
            }
            return "reserved:" + orderId;
        }

        int attempts() {
            return attempts.get();
        }
    }
}
