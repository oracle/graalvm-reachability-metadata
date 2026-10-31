/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(Integer.MIN_VALUE)
public class ResourceLeakDetectorFactoryInnerDefaultResourceLeakDetectorFactoryTest {
    @Test
    @SuppressWarnings("deprecation")
    void constructsConfiguredDetectorsWithBothSignatures() {
        String property = "io.netty.customResourceLeakDetector";
        String previous = System.getProperty(property);
        System.setProperty(property, CustomResourceLeakDetector.class.getName());
        try {
            ResourceLeakDetectorFactory factory = ResourceLeakDetectorFactory.instance();
            ResourceLeakDetector<Object> current = factory.newResourceLeakDetector(Object.class, 17);
            CustomResourceLeakDetector<?> custom = Assertions.assertInstanceOf(CustomResourceLeakDetector.class, current);
            Assertions.assertEquals(Object.class, custom.resourceType);
            Assertions.assertEquals(17, custom.samplingInterval);
            Assertions.assertFalse(custom.obsolete);
            assertTracksResource(current);

            ResourceLeakDetector<Object> legacy = factory.newResourceLeakDetector(Object.class, 19, 23L);
            CustomResourceLeakDetector<?> obsolete = Assertions.assertInstanceOf(CustomResourceLeakDetector.class, legacy);
            Assertions.assertEquals(Object.class, obsolete.resourceType);
            Assertions.assertEquals(19, obsolete.samplingInterval);
            Assertions.assertEquals(23L, obsolete.maxActive);
            Assertions.assertTrue(obsolete.obsolete);
            assertTracksResource(legacy);
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    private static void assertTracksResource(ResourceLeakDetector<Object> detector) {
        Object resource = new Object();
        ResourceLeakTracker<Object> tracker = detector.trackForcibly(resource);
        Assertions.assertNotNull(tracker);
        tracker.record("used by test");
        Assertions.assertTrue(tracker.close(resource));
        Assertions.assertFalse(tracker.close(resource));
    }

    public static final class CustomResourceLeakDetector<T> extends ResourceLeakDetector<T> {
        private final Class<?> resourceType;
        private final int samplingInterval;
        private final long maxActive;
        private final boolean obsolete;

        public CustomResourceLeakDetector(Class<?> resourceType, int samplingInterval) {
            super(resourceType, samplingInterval);
            this.resourceType = resourceType;
            this.samplingInterval = samplingInterval;
            this.maxActive = Long.MAX_VALUE;
            this.obsolete = false;
        }

        @SuppressWarnings("deprecation")
        public CustomResourceLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
            super(resourceType, samplingInterval, maxActive);
            this.resourceType = resourceType;
            this.samplingInterval = samplingInterval;
            this.maxActive = maxActive;
            this.obsolete = true;
        }
    }
}
