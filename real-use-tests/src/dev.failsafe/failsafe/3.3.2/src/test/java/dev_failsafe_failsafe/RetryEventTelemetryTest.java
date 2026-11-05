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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's event listener subsystem while a checkout service reports retry progress.
public class RetryEventTelemetryTest {

    @Test
    @Timeout(60)
    void recordsRetryAttemptsAndSuccessfulCheckout() {
        CheckoutService checkout = new CheckoutService();
        RetryTelemetry telemetry = new RetryTelemetry();
        RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                .withMaxRetries(2)
                .onRetry(event -> telemetry.recordRetry(event.getLastException()))
                .onSuccess(event -> telemetry.recordSuccess(event.getResult(), event.getAttemptCount()))
                .build();
        FailsafeExecutor<String> failsafe = Failsafe.with(retryPolicy);

        String result = failsafe.get(() -> checkout.confirm("cart-1042"));

        assertThat(result).isEqualTo("confirmed:cart-1042");
        assertThat(checkout.calls()).isEqualTo(3);
        assertThat(telemetry.retryCount()).isEqualTo(2);
        assertThat(telemetry.lastRetryFailure()).hasMessage("payment gateway unavailable");
        assertThat(telemetry.successfulResult()).isEqualTo("confirmed:cart-1042");
        assertThat(telemetry.successfulAttempts()).isEqualTo(3);
    }

    private static final class CheckoutService {
        private final AtomicInteger calls = new AtomicInteger();

        String confirm(String cartId) {
            int call = calls.incrementAndGet();
            if (call < 3) {
                throw new IllegalStateException("payment gateway unavailable");
            }
            return "confirmed:" + cartId;
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class RetryTelemetry {
        private final AtomicInteger retryCount = new AtomicInteger();
        private final AtomicReference<Throwable> lastRetryFailure = new AtomicReference<>();
        private final AtomicReference<String> successfulResult = new AtomicReference<>();
        private final AtomicInteger successfulAttempts = new AtomicInteger();

        void recordRetry(Throwable failure) {
            retryCount.incrementAndGet();
            lastRetryFailure.set(failure);
        }

        void recordSuccess(String result, int attempts) {
            successfulResult.set(result);
            successfulAttempts.set(attempts);
        }

        int retryCount() {
            return retryCount.get();
        }

        Throwable lastRetryFailure() {
            return lastRetryFailure.get();
        }

        String successfulResult() {
            return successfulResult.get();
        }

        int successfulAttempts() {
            return successfulAttempts.get();
        }
    }
}
