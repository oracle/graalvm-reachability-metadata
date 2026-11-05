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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises Failsafe's fallback subsystem while a product catalog serves a cached item when its primary source is unavailable.
public class FallbackCatalogTest {

    @Test
    @Timeout(60)
    void servesCachedProductWhenPrimaryCatalogFails() {
        ProductCatalog catalog = new ProductCatalog();
        Fallback<Product> fallback = Fallback.of(new Product("sku-42", "Cached keyboard"));
        FailsafeExecutor<Product> failsafe = Failsafe.with(fallback);

        Product product = failsafe.get(() -> catalog.find("sku-42"));

        assertThat(product.sku()).isEqualTo("sku-42");
        assertThat(product.name()).isEqualTo("Cached keyboard");
        assertThat(catalog.calls()).isEqualTo(1);
    }

    private static final class ProductCatalog {
        private final AtomicInteger calls = new AtomicInteger();

        Product find(String sku) {
            calls.incrementAndGet();
            throw new IllegalStateException("catalog unavailable");
        }

        int calls() {
            return calls.get();
        }
    }

    private record Product(String sku, String name) {
    }
}
