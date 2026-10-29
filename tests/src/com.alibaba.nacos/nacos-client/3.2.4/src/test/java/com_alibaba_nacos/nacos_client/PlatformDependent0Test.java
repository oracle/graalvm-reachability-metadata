/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class PlatformDependent0Test {
    @Test
    void alignsDirectBuffersForTransportWrites() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(64);
        try {
            ByteBuffer aligned = PlatformDependent.alignDirectBuffer(buffer, 8);
            aligned.putLong(0, 42L);
            assertTrue(aligned.isDirect());
            assertEquals(42L, aligned.getLong(0));
            assertEquals(0, aligned.capacity() % 8);
            assertEquals(16, PlatformDependent.allocateUninitializedArray(16).length);
        } finally {
            PlatformDependent.freeDirectBuffer(buffer);
        }
    }
}
