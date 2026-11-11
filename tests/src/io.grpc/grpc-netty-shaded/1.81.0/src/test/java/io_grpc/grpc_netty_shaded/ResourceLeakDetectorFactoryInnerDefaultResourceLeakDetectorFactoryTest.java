/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.util.ResourceLeakDetector;
import io.grpc.netty.shaded.io.netty.util.ResourceLeakDetectorFactory;
import io.grpc.netty.shaded.io.netty.util.ResourceLeakTracker;

public class ResourceLeakDetectorFactoryInnerDefaultResourceLeakDetectorFactoryTest {
    @Test
    void configuredDetectorTracksAndClosesResources() {
        ResourceLeakDetector<Object> detector = ResourceLeakDetectorFactory.instance()
                .newResourceLeakDetector(Object.class, 7);
        ConfiguredLeakDetector<?> configured = assertInstanceOf(ConfiguredLeakDetector.class, detector);
        assertEquals(Object.class, configured.resourceType);
        assertEquals(7, configured.interval);
        assertFalse(configured.legacy);
        assertResourceLifecycle(detector);
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyFactorySignaturePassesItsConfigurationToTheDetector() {
        ResourceLeakDetector<Object> detector = ResourceLeakDetectorFactory.instance()
                .newResourceLeakDetector(Object.class, 13, 101L);
        ConfiguredLeakDetector<?> configured = assertInstanceOf(ConfiguredLeakDetector.class, detector);
        assertEquals(Object.class, configured.resourceType);
        assertEquals(13, configured.interval);
        assertEquals(101L, configured.maxActive);
        assertTrue(configured.legacy);
        assertResourceLifecycle(detector);
    }

    private static void assertResourceLifecycle(ResourceLeakDetector<Object> detector) {
        Object resource = new Object();
        ResourceLeakTracker<Object> tracker = detector.trackForcibly(resource);
        assertNotNull(tracker);
        tracker.record("request buffer consumed");
        assertTrue(tracker.close(resource));
        assertFalse(tracker.close(resource));
    }
}
