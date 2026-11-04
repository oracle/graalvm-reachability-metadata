/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.Bulkhead;
import dev.failsafe.BulkheadFullException;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises Failsafe's bulkhead subsystem while a checkout service limits concurrent reservations.
public class BulkheadCheckoutTest {

    @Test
    @Timeout(60)
    void rejectsCheckoutWhileTheOnlyReservationSlotIsBusy() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CheckoutService checkout = new CheckoutService();
        try {
            Bulkhead<String> bulkhead = Bulkhead.<String>builder(1)
                    .withMaxWaitTime(Duration.ZERO)
                    .build();
            FailsafeExecutor<String> failsafe = Failsafe.with(bulkhead).with(workers);

            CompletableFuture<String> firstCheckout = failsafe.getAsync(checkout::reserve);
            assertThat(checkout.awaitStarted()).isTrue();

            CompletableFuture<String> rejectedCheckout = failsafe.getAsync(checkout::reserve);
            assertThatThrownBy(() -> rejectedCheckout.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BulkheadFullException.class);

            checkout.allowCompletion();
            assertThat(firstCheckout.get(10, TimeUnit.SECONDS)).isEqualTo("reservation-confirmed");
            assertThat(checkout.calls()).isEqualTo(1);
        } finally {
            checkout.allowCompletion();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class CheckoutService {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch completionAllowed = new CountDownLatch(1);

        String reserve() throws InterruptedException {
            calls.incrementAndGet();
            started.countDown();
            completionAllowed.await(10, TimeUnit.SECONDS);
            return "reservation-confirmed";
        }

        boolean awaitStarted() throws InterruptedException {
            return started.await(10, TimeUnit.SECONDS);
        }

        void allowCompletion() {
            completionAllowed.countDown();
        }

        int calls() {
            return calls.get();
        }
    }
}
