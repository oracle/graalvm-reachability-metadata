/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.Call;
import dev.failsafe.ExecutionContext;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.ContextualSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's cancellable call subsystem while an order service cooperates with cancellation.
public class CancellableCallOrderTest {

    @Test
    @Timeout(60)
    void notifiesAnOrderServiceWhenAReservationIsCancelled() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        OrderService orders = new OrderService();
        try {
            RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                    .withMaxRetries(1)
                    .build();
            FailsafeExecutor<String> failsafe = Failsafe.with(retryPolicy);
            ContextualSupplier<String, String> operation = execution ->
                    orders.reserve("order-1042", execution);
            Call<String> reservation = failsafe.newCall(operation);

            Future<String> result = workers.submit(reservation::execute);
            assertThat(orders.awaitStarted()).isTrue();

            assertThat(reservation.cancel(false)).isTrue();
            assertThat(orders.awaitCancellation()).isTrue();

            orders.allowCompletion();
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("reserved:order-1042");
            assertThat(reservation.isCancelled()).isTrue();
            assertThat(orders.calls()).isEqualTo(1);
        } finally {
            orders.allowCompletion();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class OrderService {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch cancellationObserved = new CountDownLatch(1);
        private final CountDownLatch completionAllowed = new CountDownLatch(1);

        String reserve(String orderId, ExecutionContext<String> execution) throws InterruptedException {
            calls.incrementAndGet();
            execution.onCancel(cancellationObserved::countDown);
            started.countDown();
            completionAllowed.await(10, TimeUnit.SECONDS);
            return "reserved:" + orderId;
        }

        boolean awaitStarted() throws InterruptedException {
            return started.await(10, TimeUnit.SECONDS);
        }

        boolean awaitCancellation() throws InterruptedException {
            return cancellationObserved.await(10, TimeUnit.SECONDS);
        }

        void allowCompletion() {
            completionAllowed.countDown();
        }

        int calls() {
            return calls.get();
        }
    }
}
