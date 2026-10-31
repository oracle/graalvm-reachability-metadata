/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.util.ResourceLeakDetector;
import com.couchbase.client.core.deps.io.netty.util.ResourceLeakTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceLeakDetectorTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ResourceLeakDetector.addExclusions(TrackedAllocation.class, "create");
        ResourceLeakDetector.Level previous = ResourceLeakDetector.getLevel();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        try {
            Object resource = TrackedAllocation.create();
            ResourceLeakDetector<Object> detector = new ResourceLeakDetector<>(Object.class, 1);
            ResourceLeakTracker<Object> tracker = detector.track(resource);
            assertNotNull(tracker);
            tracker.record("in use");
            assertTrue(tracker.close(resource));
        } finally {
            ResourceLeakDetector.setLevel(previous);
        }
    }

    public static class TrackedAllocation {
        public static Object create() {
            return new Object();
        }
    }
}
