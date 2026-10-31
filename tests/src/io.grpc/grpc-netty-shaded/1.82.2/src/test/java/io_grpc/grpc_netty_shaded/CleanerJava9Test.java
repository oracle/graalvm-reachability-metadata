/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.UnpooledByteBufAllocator;

public class CleanerJava9Test {
    @Test
    void releasesUnpooledDirectBufferMemory() {
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        ByteBuf buffer = allocator.directBuffer(256);
        try {
            buffer.writeLong(123456789L);
            assertEquals(123456789L, buffer.readLong());
            assertTrue(allocator.metric().usedDirectMemory() >= 256);
        } finally {
            assertTrue(buffer.release());
        }
        assertEquals(0, allocator.metric().usedDirectMemory());
    }
}
