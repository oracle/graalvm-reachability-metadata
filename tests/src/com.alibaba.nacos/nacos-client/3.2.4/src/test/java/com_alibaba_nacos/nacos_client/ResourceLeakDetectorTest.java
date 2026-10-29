/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.ResourceLeakDetector;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.ResourceLeakTracker;
import org.junit.jupiter.api.Test;

public class ResourceLeakDetectorTest {
    @Test
    void excludesAllocationHelpersFromLeakRecords() {
        ResourceLeakDetector.addExclusions(ResourceLeakDetectorTest.class, "newTrackedResource");
        Object resource = newTrackedResource();
        ResourceLeakDetector<Object> detector = new ResourceLeakDetector<>(Object.class, 1);
        ResourceLeakTracker<Object> tracker = detector.trackForcibly(resource);
        tracker.record("config buffer allocated");
        assertTrue(tracker.close(resource));
        assertFalse(tracker.close(resource));
    }

    public static Object newTrackedResource() {
        return new Object();
    }
}
