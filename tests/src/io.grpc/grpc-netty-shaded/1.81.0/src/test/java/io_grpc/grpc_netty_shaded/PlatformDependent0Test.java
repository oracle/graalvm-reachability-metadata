/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent;

public class PlatformDependent0Test {
    @Test
    void alignsDirectBufferSlicesWhileSharingTheirStorage() {
        ByteBuffer original = ByteBuffer.allocateDirect(256);
        try {
            original.position(1);
            ByteBuffer aligned = PlatformDependent.alignDirectBuffer(original, 64);
            assertTrue(aligned.isDirect());
            assertEquals(0, aligned.alignmentOffset(0, 64));
            aligned.putInt(0, 123456);
            assertEquals(123456, aligned.getInt(0));
            assertTrue(aligned.capacity() >= 128);
        } finally {
            PlatformDependent.freeDirectBuffer(original);
        }
    }

    @Test
    void allocatedByteArraysCanStorePayloads() {
        byte[] bytes = PlatformDependent.allocateUninitializedArray(4096);
        assertEquals(4096, bytes.length);
        bytes[0] = 17;
        bytes[4095] = 23;
        assertEquals(17, bytes[0]);
        assertEquals(23, bytes[4095]);
    }
}
