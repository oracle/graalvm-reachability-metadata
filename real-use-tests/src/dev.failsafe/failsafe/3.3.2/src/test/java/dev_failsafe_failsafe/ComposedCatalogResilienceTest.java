/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_failsafe_failsafe;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's policy composition subsystem while a product catalog retries before using a cache.
public class ComposedCatalogResilienceTest {

    @Test
    @Timeout(60)
    void retriesCatalogBeforeServingTheFallback() {
        RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
                .withMaxRetries(1)
                .build();
        Fallback<String> fallback = Fallback.of("cached:sku-42");
        FailsafeExecutor<String> resilientCatalog = Failsafe.with(fallback).compose(retryPolicy);
        ProductCatalog catalog = new ProductCatalog();

        String product = resilientCatalog.get(() -> catalog.find("sku-42"));

        assertThat(product).isEqualTo("cached:sku-42");
        assertThat(catalog.calls()).isEqualTo(2);
    }

    private static final class ProductCatalog {
        private final AtomicInteger calls = new AtomicInteger();

        String find(String sku) {
            calls.incrementAndGet();
            throw new IllegalStateException("catalog unavailable");
        }

        int calls() {
            return calls.get();
        }
    }
}
