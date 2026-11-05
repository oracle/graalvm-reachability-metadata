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

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's result-based retry subsystem while a shipment service reports eventual fulfillment.
public class ResultRetryShipmentTest {

    @Test
    @Timeout(60)
    void pollsUntilShipmentIsReadyWhenTheServiceReturnsPending() {
        RetryPolicy<ShipmentStatus> retryPolicy = RetryPolicy.<ShipmentStatus>builder()
                .handleResultIf(status -> status.state().equals("PENDING"))
                .withMaxRetries(2)
                .build();
        FailsafeExecutor<ShipmentStatus> failsafe = Failsafe.with(retryPolicy);
        ShipmentService shipments = new ShipmentService();

        ShipmentStatus status = failsafe.get(() -> shipments.status("shipment-1042"));

        assertThat(status.state()).isEqualTo("READY");
        assertThat(status.shipmentId()).isEqualTo("shipment-1042");
        assertThat(shipments.polls()).isEqualTo(3);
    }

    private static final class ShipmentService {
        private final AtomicInteger polls = new AtomicInteger();

        ShipmentStatus status(String shipmentId) {
            int poll = polls.incrementAndGet();
            String state = poll < 3 ? "PENDING" : "READY";
            return new ShipmentStatus(shipmentId, state);
        }

        int polls() {
            return polls.get();
        }
    }

    private record ShipmentStatus(String shipmentId, String state) {
    }
}
