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
    void validatesExclusionMethodsAndTracksResourceLifecycle() {
        ResourceLeakDetector.addExclusions(TrackedResource.class, "close");
        final ResourceLeakDetector<TrackedResource> detector =
                new ResourceLeakDetector<>(TrackedResource.class, 1);
        final TrackedResource resource = new TrackedResource();
        final ResourceLeakTracker<TrackedResource> tracker = detector.trackForcibly(resource);

        assertThat(tracker).isNotNull();
        tracker.record("used");
        assertThat(tracker.close(resource)).isTrue();
    }

    public static final class TrackedResource {
        public void close() {
        }
    }
}
