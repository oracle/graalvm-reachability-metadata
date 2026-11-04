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
import dev.failsafe.event.ExecutionScheduledEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's scheduled retry subsystem while a pricing service recovers after a transient failure.
public class ScheduledRetryPricingTest {

    @Test
    @Timeout(60)
    void resumesPriceLookupAfterScheduledRetry() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PriceService prices = new PriceService();
        AtomicReference<Duration> scheduledDelay = new AtomicReference<>();
        try {
            RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                    .handle(IllegalStateException.class)
                    .withMaxRetries(1)
                    .withDelay(Duration.ofMillis(25))
                    .onRetryScheduled(event -> recordDelay(event, scheduledDelay))
                    .build();
            FailsafeExecutor<String> failsafe = Failsafe.with(retryPolicy).with(scheduler);

            String price = failsafe.get(() -> prices.lookup("sku-42"));

            assertThat(price).isEqualTo("price:sku-42:1999");
            assertThat(scheduledDelay).hasValue(Duration.ofMillis(25));
            assertThat(prices.calls()).isEqualTo(2);
        } finally {
            scheduler.shutdownNow();
            assertThat(scheduler.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void recordDelay(ExecutionScheduledEvent<String> event,
                                    AtomicReference<Duration> scheduledDelay) {
        scheduledDelay.set(event.getDelay());
    }

    private static final class PriceService {
        private final AtomicInteger calls = new AtomicInteger();

        String lookup(String sku) {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("pricing service unavailable");
            }
            return "price:" + sku + ":1999";
        }

        int calls() {
            return calls.get();
        }
    }
}
