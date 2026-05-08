/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.ResourceLeak;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import io.netty.util.ResourceLeakDetectorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceLeakDetectorFactoryInnerDefaultResourceLeakDetectorFactoryTest {
    private static final String CUSTOM_LEAK_DETECTOR_PROPERTY = "io.netty.customResourceLeakDetector";

    static {
        System.setProperty(CUSTOM_LEAK_DETECTOR_PROPERTY, ResourceLeakDetector.class.getName());
    }

    @Test
    void createsConfiguredResourceLeakDetectorFromSystemProperty() {
        System.setProperty(CUSTOM_LEAK_DETECTOR_PROPERTY, ResourceLeakDetector.class.getName());
        Level previousLevel = ResourceLeakDetector.getLevel();
        ResourceLeakDetector.setLevel(Level.PARANOID);
        try {
            ResourceLeakDetector<StringBuilder> detector = ResourceLeakDetectorFactory.instance()
                    .newResourceLeakDetector(StringBuilder.class, 1, 7L);

            ResourceLeak leak = detector.open(new StringBuilder("tracked"));

            assertThat(detector).isNotNull();
            assertThat(leak).isNotNull();
            leak.close();
        } finally {
            ResourceLeakDetector.setLevel(previousLevel);
        }
    }
}
