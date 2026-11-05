/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingSetInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' non-blocking primitive integer set subsystem to maintain active feature flags.
public class NonBlockingFeatureRegistryTest {
    private static final int FEATURE_COUNT = 6;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void registersFeaturesFromWorkersAndRetiresThem() throws Exception {
        FeatureRegistry registry = new FeatureRegistry();
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> payments = workers.submit(() -> registerFeatures(
                    registry, new int[] {101, 102, 103, 104}, workersReady, start));
            Future<?> fulfilment = workers.submit(() -> registerFeatures(
                    registry, new int[] {103, 104, 105, 106}, workersReady, start));

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            payments.get(10, TimeUnit.SECONDS);
            fulfilment.get(10, TimeUnit.SECONDS);

            assertEquals(FEATURE_COUNT, registry.size());
            assertTrue(registry.isEnabled(101));
            assertTrue(registry.isEnabled(106));
            assertEquals(Set.of(101, 102, 103, 104, 105, 106), registry.snapshot());
            assertFalse(registry.enable(103));

            assertTrue(registry.disable(102));
            assertFalse(registry.isEnabled(102));
            assertFalse(registry.disable(999));
            assertEquals(FEATURE_COUNT - 1, registry.size());
            assertEquals(Set.of(101, 103, 104, 105, 106), registry.snapshot());
        } finally {
            workers.shutdownNow();
        }
    }

    private static void registerFeatures(FeatureRegistry registry, int[] featureIds,
            CountDownLatch workersReady, CountDownLatch start) {
        workersReady.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The feature registry did not start");
            }
            for (int featureId : featureIds) {
                registry.enable(featureId);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Feature registration was interrupted", interrupted);
        }
    }

    private static final class FeatureRegistry {
        private final NonBlockingSetInt enabledFeatures = new NonBlockingSetInt();

        private boolean enable(int featureId) {
            return enabledFeatures.add(featureId);
        }

        private boolean isEnabled(int featureId) {
            return enabledFeatures.contains(featureId);
        }

        private boolean disable(int featureId) {
            return enabledFeatures.remove(featureId);
        }

        private int size() {
            return enabledFeatures.size();
        }

        private Set<Integer> snapshot() {
            Set<Integer> snapshot = new HashSet<>();
            enabledFeatures.iterator().forEachRemaining(snapshot::add);
            return snapshot;
        }
    }
}
