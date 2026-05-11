/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.PooledByteBufAllocatorL;
import io.netty.buffer.UnsafeDirectLittleEndian;
import org.junit.jupiter.api.Test;

public class PooledByteBufAllocatorLInnerInnerAllocatorTest {
    @Test
    void initializesAllocatorFromNettyDirectArenas() {
        PooledByteBufAllocatorL allocator = new PooledByteBufAllocatorL();

        assertTrue(allocator.getChunkSize() > 0);
        assertEquals(0, allocator.getHugeBufferCount());
        assertEquals(0, allocator.getHugeBufferSize());
    }

    @Test
    void allocatesAndReleasesDirectLittleEndianBuffer() {
        PooledByteBufAllocatorL allocator = new PooledByteBufAllocatorL();
        UnsafeDirectLittleEndian buffer = allocator.allocate(64);

        try {
            assertTrue(buffer.capacity() >= 64);
            assertNotEquals(0, buffer.memoryAddress());
            buffer.setLong(0, 0x0102030405060708L);
            assertEquals(0x0102030405060708L, buffer.getLong(0));
        } finally {
            buffer.release();
        }
    }
}
