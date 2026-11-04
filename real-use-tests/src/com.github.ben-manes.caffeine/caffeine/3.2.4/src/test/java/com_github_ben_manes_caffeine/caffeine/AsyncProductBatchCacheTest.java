/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Caffeine's asynchronous loading-cache subsystem for a product catalog.
 * A product page requests several products together and reuses the completed results.
 */
public class AsyncProductBatchCacheTest {
    @Test
    @Timeout(60)
    public void loadsAndReusesProductsForAnAsynchronousBatchRequest() throws Exception {
        ExecutorService loaderExecutor = Executors.newSingleThreadExecutor();
        try {
            AtomicInteger batchLoads = new AtomicInteger();
            AsyncCacheLoader<String, Product> loader = AsyncCacheLoader.bulk(skus -> {
                batchLoads.incrementAndGet();
                Map<String, Product> loaded = new HashMap<>();
                for (String sku : skus) {
                    loaded.put(sku, catalog().get(sku));
                }
                return loaded;
            });
            AsyncLoadingCache<String, Product> products = Caffeine.newBuilder()
                    .executor(loaderExecutor)
                    .buildAsync(loader);

            CompletableFuture<Map<String, Product>> batchFuture =
                    products.getAll(List.of("sku-100", "sku-200"));
            Map<String, Product> batch = batchFuture.get(10, TimeUnit.SECONDS);

            assertEquals(Map.of(
                    "sku-100", new Product("sku-100", "Trail shoes"),
                    "sku-200", new Product("sku-200", "Running jacket")), batch);
            assertEquals(1, batchLoads.get());
            assertSame(batch.get("sku-100"), products.get("sku-100").get(10, TimeUnit.SECONDS));
            assertSame(batch.get("sku-100"), products.synchronous().getIfPresent("sku-100"));
            assertSame(batch.get("sku-200"), products.synchronous().getIfPresent("sku-200"));
        } finally {
            loaderExecutor.shutdownNow();
            assertTrue(loaderExecutor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static Map<String, Product> catalog() {
        return Map.of(
                "sku-100", new Product("sku-100", "Trail shoes"),
                "sku-200", new Product("sku-200", "Running jacket"));
    }

    private record Product(String sku, String name) {
    }
}
