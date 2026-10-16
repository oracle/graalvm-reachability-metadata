/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.io.netty.util.ResourceLeakDetector;
import org.apache.pulsar.shade.io.netty.util.ResourceLeakTracker;
import org.junit.jupiter.api.Test;

public class ResourceLeakDetectorTest {
    @Test
    void excludesKnownFramesAndTracksResourceClosure() {
        ResourceLeakDetector.addExclusions(ResourceLeakDetectorTest.class, "excludedFrame");
        final ResourceLeakDetector<Object> detector = new ResourceLeakDetector<>(Object.class, 1);
        final Object resource = new Object();
        final ResourceLeakTracker<Object> tracker = detector.trackForcibly(resource);

        assertThat(tracker).isNotNull();
        assertThat(tracker.close(resource)).isTrue();
    }

    public void excludedFrame() {
    }
}
