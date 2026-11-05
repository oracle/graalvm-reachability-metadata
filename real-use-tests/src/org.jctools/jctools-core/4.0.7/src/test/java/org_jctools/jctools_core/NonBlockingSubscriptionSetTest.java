/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingHashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' non-blocking hash set subsystem to deduplicate subscriptions registered by workers.
public class NonBlockingSubscriptionSetTest {
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void deduplicatesConcurrentSubscriptionsAndRemovesRetiredOnes() throws Exception {
        SubscriptionRegistry registry = new SubscriptionRegistry();
        List<List<Subscription>> batches = List.of(
                List.of(new Subscription("orders", "email"),
                        new Subscription("billing", "email"),
                        new Subscription("orders", "email")),
                List.of(new Subscription("inventory", "email"),
                        new Subscription("billing", "email"),
                        new Subscription("shipping", "email")),
                List.of(new Subscription("orders", "email"),
                        new Subscription("fraud", "email"),
                        new Subscription("inventory", "email")));
        CountDownLatch workersReady = new CountDownLatch(batches.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(batches.size());
        List<Future<?>> completions = new ArrayList<>();
        try {
            for (List<Subscription> batch : batches) {
                completions.add(workers.submit(() -> {
                    registerBatch(registry, batch, workersReady, start);
                    return null;
                }));
            }

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> completion : completions) {
                completion.get(10, TimeUnit.SECONDS);
            }

            Set<Subscription> expected = Set.of(
                    new Subscription("orders", "email"),
                    new Subscription("billing", "email"),
                    new Subscription("inventory", "email"),
                    new Subscription("shipping", "email"),
                    new Subscription("fraud", "email"));
            assertEquals(expected, registry.snapshot());
            assertEquals(expected.size(), registry.size());
            assertTrue(registry.contains(new Subscription("orders", "email")));
            assertEquals(new Subscription("orders", "email"),
                    registry.find(new Subscription("orders", "email")));
            assertFalse(registry.register(new Subscription("orders", "email")));

            assertTrue(registry.remove(new Subscription("fraud", "email")));
            assertFalse(registry.contains(new Subscription("fraud", "email")));
            assertEquals(expected.size() - 1, registry.size());
        } finally {
            workers.shutdownNow();
        }
    }

    private static void registerBatch(SubscriptionRegistry registry, List<Subscription> batch,
            CountDownLatch workersReady, CountDownLatch start) throws InterruptedException {
        workersReady.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("The subscription registry did not start");
        }
        for (Subscription subscription : batch) {
            registry.register(subscription);
        }
    }

    private static final class SubscriptionRegistry {
        private final NonBlockingHashSet<Subscription> subscriptions = new NonBlockingHashSet<>();

        private boolean register(Subscription subscription) {
            return subscriptions.add(subscription);
        }

        private boolean contains(Subscription subscription) {
            return subscriptions.contains(subscription);
        }

        private Subscription find(Subscription subscription) {
            return subscriptions.get(subscription);
        }

        private boolean remove(Subscription subscription) {
            return subscriptions.remove(subscription);
        }

        private int size() {
            return subscriptions.size();
        }

        private Set<Subscription> snapshot() {
            Set<Subscription> snapshot = new HashSet<>();
            subscriptions.iterator().forEachRemaining(snapshot::add);
            return snapshot;
        }
    }

    private record Subscription(String topic, String channel) {
    }
}
