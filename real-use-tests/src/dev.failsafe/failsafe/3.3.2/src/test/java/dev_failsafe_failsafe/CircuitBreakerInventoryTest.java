/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises Failsafe's circuit breaker subsystem while an inventory client protects a product lookup.
public class CircuitBreakerInventoryTest {

    @Test
    @Timeout(60)
    void stopsCallingInventoryAfterRepeatedFailures() {
        CircuitBreaker<String> circuitBreaker = CircuitBreaker.<String>builder()
                .withFailureThreshold(2)
                .build();
        FailsafeExecutor<String> failsafe = Failsafe.with(circuitBreaker);
        InventoryClient inventory = new InventoryClient();

        assertThatThrownBy(() -> failsafe.get(() -> inventory.find("sku-42")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inventory unavailable");
        assertThat(circuitBreaker.isClosed()).isTrue();

        assertThatThrownBy(() -> failsafe.get(() -> inventory.find("sku-42")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inventory unavailable");
        assertThat(circuitBreaker.isOpen()).isTrue();

        assertThatThrownBy(() -> failsafe.get(() -> inventory.find("sku-42")))
                .isInstanceOf(CircuitBreakerOpenException.class);
        assertThat(inventory.calls()).isEqualTo(2);
    }

    private static final class InventoryClient {
        private final AtomicInteger calls = new AtomicInteger();

        String find(String sku) {
            calls.incrementAndGet();
            throw new IllegalStateException("inventory unavailable");
        }

        int calls() {
            return calls.get();
        }
    }
}
