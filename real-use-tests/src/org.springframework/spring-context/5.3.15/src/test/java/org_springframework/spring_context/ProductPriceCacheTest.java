/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Spring programmatic caching to reuse catalog prices and invalidate them when a merchant updates a product.
public class ProductPriceCacheTest {

    @Test
    @Timeout(60)
    public void reusesPricesUntilAnUpdateInvalidatesOnlyTheChangedProduct() {
        CacheManager cacheManager = new ConcurrentMapCacheManager("productPrices");
        ProductCatalog catalog = new ProductCatalog(cacheManager, Map.of(
                "coffee", new BigDecimal("12.50"),
                "tea", new BigDecimal("8.00")));

        assertEquals(new BigDecimal("25.00"), catalog.lineTotal("coffee", 2));
        assertEquals(new BigDecimal("24.00"), catalog.lineTotal("tea", 3));
        assertEquals(2, catalog.priceLoads);

        // Different basket quantities reuse the same product price, not a cached line total.
        assertEquals(new BigDecimal("37.50"), catalog.lineTotal("coffee", 3));
        assertEquals(new BigDecimal("8.00"), catalog.lineTotal("tea", 1));
        assertEquals(2, catalog.priceLoads);

        catalog.updatePrice("coffee", new BigDecimal("10.00"));

        assertEquals(new BigDecimal("30.00"), catalog.lineTotal("coffee", 3));
        assertEquals(3, catalog.priceLoads);
        assertEquals(new BigDecimal("16.00"), catalog.lineTotal("tea", 2));
        assertEquals(new BigDecimal("20.00"), catalog.lineTotal("coffee", 2));
        assertEquals(3, catalog.priceLoads);
    }

    public static final class ProductCatalog {
        private final Map<String, BigDecimal> prices;
        private final Cache priceCache;
        private int priceLoads;

        public ProductCatalog(CacheManager cacheManager, Map<String, BigDecimal> initialPrices) {
            prices = new HashMap<>(initialPrices);
            priceCache = Objects.requireNonNull(cacheManager.getCache("productPrices"));
        }

        public BigDecimal lineTotal(String product, int quantity) {
            BigDecimal price = priceCache.get(product, () -> loadPrice(product));
            return price.multiply(BigDecimal.valueOf(quantity));
        }

        public void updatePrice(String product, BigDecimal price) {
            prices.put(product, price);
            priceCache.evict(product);
        }

        private BigDecimal loadPrice(String product) {
            priceLoads++;
            return Objects.requireNonNull(prices.get(product), "Unknown product: " + product);
        }
    }
}
