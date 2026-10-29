/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

public class PlatformDependentTest {
    @Test
    void estimatesMemoryAvailableForDirectTransportBuffers() {
        assertTrue(PlatformDependent.estimateMaxDirectMemory() > 0);
    }
}
