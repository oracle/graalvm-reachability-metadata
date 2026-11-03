/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.AsyncExecution;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.AsyncRunnable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's callback-based asynchronous execution subsystem while a shipment service reports a transient failure.
public class AsyncCallbackShipmentTest {

    @Test
    @Timeout(60)
    void retriesACompletionCallbackAfterATransientFailure() throws Exception {
        ExecutorService callbacks = Executors.newSingleThreadExecutor();
        try {
            RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                    .withMaxRetries(1)
                    .build();
            FailsafeExecutor<String> failsafe = Failsafe.with(retryPolicy);
            ShipmentService shipments = new ShipmentService(callbacks);
            AsyncRunnable<String> lookup = execution -> shipments.find("shipment-1042", execution);

            CompletableFuture<String> result = failsafe.getAsyncExecution(lookup);

            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("in-transit:shipment-1042");
            assertThat(shipments.calls()).isEqualTo(2);
        } finally {
            callbacks.shutdownNow();
            assertThat(callbacks.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class ShipmentService {
        private final ExecutorService callbacks;
        private final AtomicInteger calls = new AtomicInteger();

        private ShipmentService(ExecutorService callbacks) {
            this.callbacks = callbacks;
        }

        void find(String shipmentId, AsyncExecution<String> execution) {
            callbacks.execute(() -> {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    execution.recordException(new IllegalStateException("tracking service unavailable"));
                } else {
                    execution.recordResult("in-transit:" + shipmentId);
                }
            });
        }

        int calls() {
            return calls.get();
        }
    }
}
