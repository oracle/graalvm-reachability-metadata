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
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Caffeine's refresh-after-write subsystem for a product-pricing service.
 * A stale price remains available while the cache refreshes it from the catalog.
 */
public class RefreshingProductCacheTest {
    @Test
    @Timeout(60)
    public void servesTheStalePriceWhileRefreshingItInTheBackground() throws Exception {
        AtomicLong currentTimeNanos = new AtomicLong();
        Ticker ticker = currentTimeNanos::get;
        AtomicInteger catalogRevision = new AtomicInteger(1);
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        CountDownLatch refreshCompleted = new CountDownLatch(1);
        ExecutorService refreshExecutor = Executors.newSingleThreadExecutor();
        try {
            CacheLoader<String, ProductPrice> loader = new CacheLoader<>() {
                @Override
                public ProductPrice load(String sku) {
                    return new ProductPrice(sku, catalogRevision.get());
                }

                @Override
                public ProductPrice reload(String sku, ProductPrice oldValue) {
                    refreshStarted.countDown();
                    await(releaseRefresh);
                    ProductPrice refreshed = new ProductPrice(sku, catalogRevision.get());
                    refreshCompleted.countDown();
                    return refreshed;
                }
            };
            LoadingCache<String, ProductPrice> prices = Caffeine.newBuilder()
                    .refreshAfterWrite(Duration.ofMinutes(5))
                    .ticker(ticker)
                    .executor(refreshExecutor)
                    .build(loader);

            ProductPrice initial = prices.get("sku-100");
            catalogRevision.set(2);
            currentTimeNanos.addAndGet(Duration.ofMinutes(5).toNanos() + 1);

            ProductPrice stale = prices.get("sku-100");
            assertEquals(initial, stale);
            assertTrue(refreshStarted.await(10, TimeUnit.SECONDS));
            releaseRefresh.countDown();
            assertTrue(refreshCompleted.await(10, TimeUnit.SECONDS));
            assertEquals(new ProductPrice("sku-100", 2), awaitRefresh(prices));
        } finally {
            releaseRefresh.countDown();
            refreshExecutor.shutdownNow();
            assertTrue(refreshExecutor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static ProductPrice awaitRefresh(LoadingCache<String, ProductPrice> prices)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        ProductPrice refreshed;
        do {
            refreshed = prices.getIfPresent("sku-100");
            if (new ProductPrice("sku-100", 2).equals(refreshed)) {
                return refreshed;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return refreshed;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("product refresh was not released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("product refresh was interrupted", interrupted);
        }
    }

    private record ProductPrice(String sku, int catalogRevision) {
    }
}
