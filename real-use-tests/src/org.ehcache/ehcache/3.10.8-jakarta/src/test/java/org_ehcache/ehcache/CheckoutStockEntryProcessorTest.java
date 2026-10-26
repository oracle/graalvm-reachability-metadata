/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Ehcache's JCache entry-processing subsystem for atomic checkout stock reservations.
 * A checkout validates and decrements stock in one cache operation without overselling or creating unknown products.
 */
public class CheckoutStockEntryProcessorTest {
    @Test
    @Timeout(60)
    public void reservesAvailableStockAndRejectsUnfulfillableOrders() {
        try (EhcacheCachingProvider provider = new EhcacheCachingProvider();
                CacheManager manager = provider.getCacheManager()) {
            Configuration<String, Integer> configuration = Eh107Configuration.fromEhcacheCacheConfiguration(
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(
                            String.class, Integer.class, ResourcePoolsBuilder.heap(100)));
            Cache<String, Integer> stock = manager.createCache("checkout-stock", configuration);
            stock.put("coffee", 5);
            stock.put("tea", 9);

            assertEquals(Reservation.RESERVED, stock.invoke("coffee", new ReserveStock(2)));
            assertEquals(Integer.valueOf(3), stock.get("coffee"));

            // A larger basket must not consume any of the remaining stock.
            assertEquals(Reservation.INSUFFICIENT_STOCK, stock.invoke("coffee", new ReserveStock(4)));
            assertEquals(Integer.valueOf(3), stock.get("coffee"));

            assertEquals(Reservation.RESERVED, stock.invoke("coffee", new ReserveStock(3)));
            assertEquals(Integer.valueOf(0), stock.get("coffee"));
            assertEquals(Reservation.INSUFFICIENT_STOCK, stock.invoke("coffee", new ReserveStock(1)));
            assertEquals(Integer.valueOf(0), stock.get("coffee"));

            assertEquals(Reservation.UNKNOWN_PRODUCT, stock.invoke("cocoa", new ReserveStock(1)));
            assertNull(stock.get("cocoa"));
            assertFalse(stock.containsKey("cocoa"));
            assertEquals(Integer.valueOf(9), stock.get("tea"));
        }
    }

    private enum Reservation {
        RESERVED,
        INSUFFICIENT_STOCK,
        UNKNOWN_PRODUCT
    }

    private static final class ReserveStock implements EntryProcessor<String, Integer, Reservation> {
        private final int quantity;

        private ReserveStock(int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Reservation quantity must be positive");
            }
            this.quantity = quantity;
        }

        @Override
        public Reservation process(MutableEntry<String, Integer> entry, Object... arguments) {
            if (!entry.exists()) {
                return Reservation.UNKNOWN_PRODUCT;
            }
            int available = entry.getValue();
            if (available < quantity) {
                return Reservation.INSUFFICIENT_STOCK;
            }
            entry.setValue(available - quantity);
            return Reservation.RESERVED;
        }
    }
}
