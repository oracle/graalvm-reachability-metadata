/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingHashMapLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' non-blocking primitive-key map subsystem to maintain a concurrent delivery registry.
public class NonBlockingDeliveryRegistryTest {
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void registersConcurrentDeliveriesAndHandlesRetries() throws Exception {
        DeliveryRegistry registry = new DeliveryRegistry();
        CountDownLatch producersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService producers = Executors.newFixedThreadPool(2);
        try {
            Future<Delivery> orders = submit(registry, new Delivery(41L, "orders", "order-41"),
                    producersReady, start, producers);
            Future<Delivery> billing = submit(registry, new Delivery(82L, "billing", "invoice-82"),
                    producersReady, start, producers);

            assertTrue(producersReady.await(10, TimeUnit.SECONDS));
            start.countDown();

            Delivery ordersDelivery = orders.get(10, TimeUnit.SECONDS);
            Delivery billingDelivery = billing.get(10, TimeUnit.SECONDS);
            assertEquals(new Delivery(41L, "orders", "order-41"), ordersDelivery);
            assertEquals(new Delivery(82L, "billing", "invoice-82"), billingDelivery);
            assertEquals(2, registry.size());
            assertArrayEquals(new long[] {41L, 82L}, registry.ids());

            Delivery retry = new Delivery(41L, "orders", "retry-41");
            assertSame(ordersDelivery, registry.register(retry));
            assertSame(ordersDelivery, registry.find(41L));

            Delivery dispatched = new Delivery(41L, "orders", "dispatched-41");
            assertTrue(registry.replace(ordersDelivery, dispatched));
            assertSame(dispatched, registry.find(41L));
            assertFalse(registry.remove(82L, retry));
            assertTrue(registry.remove(82L, billingDelivery));
            assertEquals(1, registry.size());
            assertTrue(registry.contains(41L));
            assertFalse(registry.contains(82L));
        } finally {
            producers.shutdownNow();
        }
    }

    private static Future<Delivery> submit(DeliveryRegistry registry, Delivery delivery,
            CountDownLatch producersReady, CountDownLatch start, ExecutorService producers) {
        return producers.submit(() -> {
            producersReady.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The delivery registry did not start");
            }
            return registry.register(delivery);
        });
    }

    private static final class DeliveryRegistry {
        private final NonBlockingHashMapLong<Delivery> deliveries = new NonBlockingHashMapLong<>();

        private Delivery register(Delivery delivery) {
            Delivery existing = deliveries.putIfAbsent(delivery.id(), delivery);
            return existing == null ? delivery : existing;
        }

        private Delivery find(long id) {
            return deliveries.get(id);
        }

        private boolean replace(Delivery expected, Delivery replacement) {
            return deliveries.replace(expected.id(), expected, replacement);
        }

        private boolean remove(long id, Delivery expected) {
            return deliveries.remove(id, expected);
        }

        private boolean contains(long id) {
            return deliveries.containsKey(id);
        }

        private int size() {
            return deliveries.size();
        }

        private long[] ids() {
            long[] ids = deliveries.keySetLong();
            Arrays.sort(ids);
            return ids;
        }
    }

    private record Delivery(long id, String stream, String payload) {
    }
}
