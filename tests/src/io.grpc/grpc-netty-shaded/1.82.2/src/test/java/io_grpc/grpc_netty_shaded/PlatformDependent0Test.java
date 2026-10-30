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
import io.grpc.netty.shaded.io.netty.buffer.PooledByteBufAllocator;

public class PlatformDependent0Test {
    @Test
    void allocatesAlignedPooledDirectBuffers() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true, 0, 1, 8192, 1, 0, 0, false, 64);
        ByteBuf buffer = allocator.directBuffer(4096);
        try {
            assertTrue(buffer.isDirect());
            buffer.writeLong(0x0102030405060708L);
            assertEquals(0x0102030405060708L, buffer.readLong());
            assertEquals(4096, buffer.capacity());
        } finally {
            buffer.release();
            allocator.freeThreadLocalCache();
        }
    }
}
