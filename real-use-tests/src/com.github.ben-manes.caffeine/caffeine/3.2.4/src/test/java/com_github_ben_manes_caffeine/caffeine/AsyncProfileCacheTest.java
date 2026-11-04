/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Caffeine's asynchronous cache subsystem for a customer profile service.
 * Concurrent requests share the in-flight profile load and observe the completed profile.
 */
public class AsyncProfileCacheTest {
    @Test
    @Timeout(60)
    public void sharesAnInFlightProfileLoadAcrossConcurrentRequests() throws Exception {
        ExecutorService loaderExecutor = Executors.newSingleThreadExecutor();
        try {
            AsyncCache<String, CustomerProfile> profiles = Caffeine.newBuilder()
                    .executor(loaderExecutor)
                    .buildAsync();
            AtomicInteger loads = new AtomicInteger();
            CountDownLatch loadStarted = new CountDownLatch(1);
            CountDownLatch releaseLoad = new CountDownLatch(1);
            BiFunction<String, Executor, CompletableFuture<CustomerProfile>> loader = (customerId, executor) -> {
                loads.incrementAndGet();
                return CompletableFuture.supplyAsync(() -> {
                    loadStarted.countDown();
                    await(releaseLoad);
                    return new CustomerProfile(customerId, "Gold");
                }, executor);
            };

            CompletableFuture<CustomerProfile> first = profiles.get("customer-42", loader);
            assertTrue(loadStarted.await(10, TimeUnit.SECONDS));
            CompletableFuture<CustomerProfile> second = profiles.get("customer-42", loader);

            assertSame(first, second);
            assertEquals(1, loads.get());

            releaseLoad.countDown();
            CustomerProfile profile = first.get(10, TimeUnit.SECONDS);
            assertEquals(new CustomerProfile("customer-42", "Gold"), profile);
            assertEquals(profile, profiles.synchronous().getIfPresent("customer-42"));
        } finally {
            loaderExecutor.shutdownNow();
            assertTrue(loaderExecutor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("profile load was not released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("profile load was interrupted", interrupted);
        }
    }

    private record CustomerProfile(String customerId, String tier) {
    }
}
