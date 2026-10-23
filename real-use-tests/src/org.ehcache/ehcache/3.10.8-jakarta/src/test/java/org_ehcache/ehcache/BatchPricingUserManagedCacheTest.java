/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.UserManagedCache;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.builders.UserManagedCacheBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Ehcache's user-managed cache subsystem for batch-scoped product pricing.
 * Each pricing batch owns its cache without a cache manager and releases it independently of other batches.
 */
public class BatchPricingUserManagedCacheTest {
    @TempDir
    Path priceDirectory;

    @Test
    @Timeout(60)
    public void reusesBatchPricesAndStartsIndependentBatchesWithFreshPrices() throws IOException {
        Path coffeePrice = priceDirectory.resolve("coffee.txt");
        Files.writeString(coffeePrice, "1200");
        Files.writeString(priceDirectory.resolve("tea.txt"), "700");

        try (PricingBatch morning = new PricingBatch(priceDirectory)) {
            assertEquals(3100L, morning.subtotal(Map.of("coffee", 2, "tea", 1)));

            // Already resolved prices are reused even when the next feed changes the source catalog.
            Files.writeString(coffeePrice, "1000");
            assertEquals(3600L, morning.subtotal(Map.of("coffee", 3)));

            try (PricingBatch afternoon = new PricingBatch(priceDirectory)) {
                assertEquals(3000L, afternoon.subtotal(Map.of("coffee", 3)));
                assertEquals(1900L, morning.subtotal(Map.of("coffee", 1, "tea", 1)));
            }

            // Closing another batch must not close this batch's standalone cache.
            assertEquals(2400L, morning.subtotal(Map.of("coffee", 2)));
        }

        // A later invocation has no stale entries from either of the completed batches.
        try (PricingBatch nextBatch = new PricingBatch(priceDirectory)) {
            assertEquals(2700L, nextBatch.subtotal(Map.of("coffee", 2, "tea", 1)));
        }
    }

    private static final class PricingBatch implements AutoCloseable {
        private final Path directory;
        private final UserManagedCache<String, Long> prices;

        private PricingBatch(Path directory) {
            this.directory = directory;
            this.prices = UserManagedCacheBuilder.newUserManagedCacheBuilder(String.class, Long.class)
                    .withResourcePools(ResourcePoolsBuilder.heap(100))
                    .build(true);
        }

        private long subtotal(Map<String, Integer> quantities) throws IOException {
            long total = 0;
            for (Map.Entry<String, Integer> line : quantities.entrySet()) {
                String sku = line.getKey();
                Long price = prices.get(sku);
                if (price == null) {
                    price = Long.parseLong(Files.readString(directory.resolve(sku + ".txt")).strip());
                    prices.put(sku, price);
                }
                total += price * line.getValue();
            }
            return total;
        }

        @Override
        public void close() {
            prices.close();
        }
    }
}
