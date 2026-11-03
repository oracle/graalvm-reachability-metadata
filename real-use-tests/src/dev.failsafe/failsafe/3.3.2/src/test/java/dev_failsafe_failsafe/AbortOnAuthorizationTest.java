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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises Failsafe's retry-abort subsystem while a payment client stops retrying after an authorization failure.
public class AbortOnAuthorizationTest {

    @Test
    @Timeout(60)
    void retriesTransientPaymentFailureButAbortsAuthorizationFailure() {
        RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                .withMaxRetries(3)
                .abortOn(AuthorizationException.class)
                .build();
        FailsafeExecutor<String> failsafe = Failsafe.with(retryPolicy);
        PaymentClient payments = new PaymentClient();

        assertThatThrownBy(() -> failsafe.get(() -> payments.charge("order-1042")))
                .isInstanceOf(AuthorizationException.class)
                .hasMessage("payment authorization declined");
        assertThat(payments.calls()).isEqualTo(2);
    }

    private static final class PaymentClient {
        private final AtomicInteger calls = new AtomicInteger();

        String charge(String orderId) throws IOException {
            if (calls.incrementAndGet() == 1) {
                throw new IOException("payment gateway temporarily unavailable");
            }
            throw new AuthorizationException("payment authorization declined");
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class AuthorizationException extends RuntimeException {
        private AuthorizationException(String message) {
            super(message);
        }
    }
}
