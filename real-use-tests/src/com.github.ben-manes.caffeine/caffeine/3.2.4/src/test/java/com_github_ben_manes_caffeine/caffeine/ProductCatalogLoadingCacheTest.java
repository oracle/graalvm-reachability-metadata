/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Exercises Caffeine's synchronous loading-cache subsystem for a product catalog.
 * A catalog request loads a missing product and reuses it for later requests.
 */
public class ProductCatalogLoadingCacheTest {
    @Test
    @Timeout(60)
    public void loadsAndReusesProductsForIndividualAndBatchRequests() {
        LoadingCache<String, Product> products = Caffeine.newBuilder()
                .maximumSize(100)
                .build(new CacheLoader<>() {
                    @Override
                    public Product load(String sku) {
                        return catalog().get(sku);
                    }

                    @Override
                    public Map<String, Product> loadAll(Set<? extends String> skus) {
                        Map<String, Product> catalog = catalog();
                        Map<String, Product> loaded = new HashMap<>();
                        for (String sku : skus) {
                            loaded.put(sku, catalog.get(sku));
                        }
                        return loaded;
                    }
                });

        Product first = products.get("sku-100");
        Product second = products.get("sku-100");
        Map<String, Product> batch = products.getAll(List.of("sku-100", "sku-200"));

        assertEquals(new Product("sku-100", "Trail shoes"), first);
        assertSame(first, second);
        assertEquals(Map.of(
                "sku-100", new Product("sku-100", "Trail shoes"),
                "sku-200", new Product("sku-200", "Running jacket")), batch);
        assertEquals(new Product("sku-200", "Running jacket"), products.getIfPresent("sku-200"));
    }

    private static Map<String, Product> catalog() {
        return Map.of(
                "sku-100", new Product("sku-100", "Trail shoes"),
                "sku-200", new Product("sku-200", "Running jacket"));
    }

    private record Product(String sku, String name) {
    }
}
