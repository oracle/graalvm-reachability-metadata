/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourceLeakDetectorFactory_DefaultResourceLeakDetectorFactoryTest {
    private static final String CUSTOM_RESOURCE_LEAK_DETECTOR_PROPERTY = "io.netty.customResourceLeakDetector";

    @Test
    @SuppressWarnings("deprecation")
    void loadsCustomLeakDetectorsFromBothSupportedConstructors() {
        String previousDetector = System.getProperty(CUSTOM_RESOURCE_LEAK_DETECTOR_PROPERTY);
        System.setProperty(CUSTOM_RESOURCE_LEAK_DETECTOR_PROPERTY, CustomResourceLeakDetector.class.getName());

        try {
            ResourceLeakDetectorFactory factory = ResourceLeakDetectorFactory.instance();

            ResourceLeakDetector<Object> detectorWithCurrentConstructor =
                    factory.newResourceLeakDetector(Object.class, 17);
            Assertions.assertInstanceOf(CustomResourceLeakDetector.class, detectorWithCurrentConstructor);
            CustomResourceLeakDetector<?> currentDetector =
                    (CustomResourceLeakDetector<?>) detectorWithCurrentConstructor;
            Assertions.assertEquals(Object.class, currentDetector.resourceType);
            Assertions.assertEquals(17, currentDetector.samplingInterval);
            Assertions.assertFalse(currentDetector.usedObsoleteConstructor);

            ResourceLeakDetector<Object> detectorWithObsoleteConstructor =
                    factory.newResourceLeakDetector(Object.class, 19, 23L);
            Assertions.assertInstanceOf(CustomResourceLeakDetector.class, detectorWithObsoleteConstructor);
            CustomResourceLeakDetector<?> obsoleteDetector =
                    (CustomResourceLeakDetector<?>) detectorWithObsoleteConstructor;
            Assertions.assertEquals(Object.class, obsoleteDetector.resourceType);
            Assertions.assertEquals(19, obsoleteDetector.samplingInterval);
            Assertions.assertEquals(23L, obsoleteDetector.maxActive);
            Assertions.assertTrue(obsoleteDetector.usedObsoleteConstructor);
        } finally {
            restorePreviousDetector(previousDetector);
        }
    }

    private static void restorePreviousDetector(String previousDetector) {
        if (previousDetector == null) {
            System.clearProperty(CUSTOM_RESOURCE_LEAK_DETECTOR_PROPERTY);
            return;
        }
        System.setProperty(CUSTOM_RESOURCE_LEAK_DETECTOR_PROPERTY, previousDetector);
    }

    public static final class CustomResourceLeakDetector<T> extends ResourceLeakDetector<T> {
        private final Class<?> resourceType;
        private final int samplingInterval;
        private final long maxActive;
        private final boolean usedObsoleteConstructor;

        public CustomResourceLeakDetector(Class<?> resourceType, int samplingInterval) {
            super(resourceType, samplingInterval);
            this.resourceType = resourceType;
            this.samplingInterval = samplingInterval;
            this.maxActive = Long.MAX_VALUE;
            this.usedObsoleteConstructor = false;
        }

        @Deprecated
        public CustomResourceLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
            super(resourceType, samplingInterval, maxActive);
            this.resourceType = resourceType;
            this.samplingInterval = samplingInterval;
            this.maxActive = maxActive;
            this.usedObsoleteConstructor = true;
        }
    }
}
