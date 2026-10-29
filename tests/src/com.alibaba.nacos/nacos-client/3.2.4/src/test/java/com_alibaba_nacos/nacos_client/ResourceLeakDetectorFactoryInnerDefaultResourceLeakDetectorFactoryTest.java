/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.ResourceLeakDetector;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.ResourceLeakDetectorFactory;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.ResourceLeakTracker;
import org.junit.jupiter.api.Test;

public class ResourceLeakDetectorFactoryInnerDefaultResourceLeakDetectorFactoryTest {
    @Test
    void createsWorkingLeakDetectorsForBothFactoryOverloads() {
        ResourceLeakDetectorFactory factory = ResourceLeakDetectorFactory.instance();
        ResourceLeakDetector<Object> current = factory.newResourceLeakDetector(Object.class, 1);
        ResourceLeakDetector<Object> legacy = factory.newResourceLeakDetector(Object.class, 1, 128);
        Object resource = new Object();
        ResourceLeakTracker<Object> first = current.trackForcibly(resource);
        ResourceLeakTracker<Object> second = legacy.trackForcibly(resource);
        assertTrue(first.close(resource));
        assertTrue(second.close(resource));
    }
}
