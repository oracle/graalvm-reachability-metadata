/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.Timeout;
import dev.failsafe.TimeoutExceededException;
import org.junit.jupiter.api.Test;

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

/// Exercises Failsafe's timeout subsystem while a payment service bounds a stalled authorization.
public class TimeoutPaymentAuthorizationTest {

    @Test
    @org.junit.jupiter.api.Timeout(60)
    void rejectsAuthorizationThatExceedsThePaymentDeadline() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        PaymentService payments = new PaymentService();
        try {
            Timeout<String> timeout = Timeout.<String>builder(Duration.ofMillis(100))
                    .withInterrupt()
                    .build();
            FailsafeExecutor<String> failsafe = Failsafe.with(timeout).with(workers);

            CompletableFuture<String> authorization = failsafe.getAsync(
                    () -> payments.authorize());
            assertThat(payments.awaitStarted()).isTrue();

            assertThatThrownBy(() -> authorization.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TimeoutExceededException.class);
            assertThat(payments.calls()).isEqualTo(1);
        } finally {
            payments.allowCompletion();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class PaymentService {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch completionAllowed = new CountDownLatch(1);

        String authorize() throws InterruptedException {
            calls.incrementAndGet();
            started.countDown();
            completionAllowed.await(10, TimeUnit.SECONDS);
            return "authorized:payment-1042";
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
