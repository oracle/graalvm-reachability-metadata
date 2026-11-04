/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Caffeine's scheduled-expiration subsystem for a shopping-cart service.
 * An idle cart is removed proactively by the scheduler and reported to the service.
 */
public class ScheduledExpirationCacheTest {
    @Test
    @Timeout(60)
    public void removesExpiredCartWithoutAnotherCacheOperation() throws Exception {
        ScheduledExecutorService expirationExecutor = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch expirationObserved = new CountDownLatch(1);
        ConcurrentLinkedQueue<RemovalEvent> removals = new ConcurrentLinkedQueue<>();
        try {
            Cache<String, ShoppingCart> carts = Caffeine.newBuilder()
                    .scheduler(Scheduler.forScheduledExecutorService(expirationExecutor))
                    .expireAfterWrite(Duration.ofMillis(100))
                    .removalListener((String cartId, ShoppingCart cart, RemovalCause cause) -> {
                        removals.add(new RemovalEvent(cartId, cart, cause));
                        expirationObserved.countDown();
                    })
                    .build();
            ShoppingCart cart = new ShoppingCart("cart-42", 3);
            carts.put(cart.id(), cart);

            assertSame(cart, carts.getIfPresent(cart.id()));
            assertTrue(expirationObserved.await(10, TimeUnit.SECONDS));

            RemovalEvent removal = removals.peek();
            assertEquals(new RemovalEvent("cart-42", cart, RemovalCause.EXPIRED), removal);
            assertNull(carts.getIfPresent(cart.id()));
            assertEquals(0, carts.estimatedSize());
        } finally {
            expirationExecutor.shutdownNow();
            assertTrue(expirationExecutor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private record ShoppingCart(String id, int itemCount) {
    }

    private record RemovalEvent(String cartId, ShoppingCart cart, RemovalCause cause) {
    }
}
