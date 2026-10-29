/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
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
