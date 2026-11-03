/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' non-blocking generic map subsystem to maintain a concurrent endpoint registry.
public class NonBlockingEndpointRegistryTest {
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void registersEndpointsAndAppliesConditionalUpdates() throws Exception {
        EndpointRegistry registry = new EndpointRegistry();
        List<List<Endpoint>> registrations = List.of(
                List.of(new Endpoint("orders", "orders-a", 8081),
                        new Endpoint("billing", "billing-a", 8082)),
                List.of(new Endpoint("inventory", "inventory-a", 8083),
                        new Endpoint("shipping", "shipping-a", 8084)));
        CountDownLatch workersReady = new CountDownLatch(registrations.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(registrations.size());
        List<Future<?>> completions = new ArrayList<>();
        try {
            for (List<Endpoint> batch : registrations) {
                completions.add(workers.submit(() -> {
                    workersReady.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The endpoint registry did not start");
                    }
                    for (Endpoint endpoint : batch) {
                        registry.register(endpoint);
                    }
                    return null;
                }));
            }

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> completion : completions) {
                completion.get(10, TimeUnit.SECONDS);
            }

            assertEquals(4, registry.size());
            assertEquals(new Endpoint("orders", "orders-a", 8081), registry.find("orders"));
            assertTrue(registry.names().containsAll(List.of("orders", "billing", "inventory", "shipping")));

            Endpoint originalOrders = registry.find("orders");
            Endpoint replacementOrders = new Endpoint("orders", "orders-b", 9081);
            assertTrue(registry.replace(originalOrders, replacementOrders));
            assertSame(replacementOrders, registry.find("orders"));
            assertFalse(registry.replace(originalOrders,
                    new Endpoint("orders", "stale", 9999)));

            assertFalse(registry.register(new Endpoint("orders", "duplicate", 7777)));
            assertTrue(registry.remove("shipping", registry.find("shipping")));
            assertFalse(registry.remove("shipping", replacementOrders));
            assertEquals(3, registry.size());
        } finally {
            workers.shutdownNow();
        }
    }

    private static final class EndpointRegistry {
        private final NonBlockingHashMap<String, Endpoint> endpoints = new NonBlockingHashMap<>();

        private boolean register(Endpoint endpoint) {
            return endpoints.putIfAbsent(endpoint.name(), endpoint) == null;
        }

        private Endpoint find(String name) {
            return endpoints.get(name);
        }

        private boolean replace(Endpoint expected, Endpoint replacement) {
            return endpoints.replace(expected.name(), expected, replacement);
        }

        private boolean remove(String name, Endpoint expected) {
            return endpoints.remove(name, expected);
        }

        private int size() {
            return endpoints.size();
        }

        private List<String> names() {
            return new ArrayList<>(endpoints.keySet());
        }
    }

    private record Endpoint(String name, String host, int port) {
    }
}
