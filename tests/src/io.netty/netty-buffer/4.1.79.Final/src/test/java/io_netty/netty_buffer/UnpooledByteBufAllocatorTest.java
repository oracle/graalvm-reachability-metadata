/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class UnpooledByteBufAllocatorTest {
    @Test
    void tracksHeapMemoryAcrossGrowthAndRelease() {
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(false);
        ByteBuf buffer = allocator.heapBuffer(8, 64);
        try {
            assertFalse(buffer.isDirect());
            assertEquals(8, allocator.metric().usedHeapMemory());
            buffer.writeLong(123456789L);
            buffer.capacity(32);
            assertEquals(32, allocator.metric().usedHeapMemory());
            assertEquals(123456789L, buffer.readLong());
        } finally {
            buffer.release();
        }
        assertEquals(0, allocator.metric().usedHeapMemory());
    }

    @Test
    void tracksDirectMemoryAcrossGrowthAndRelease() {
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        ByteBuf buffer = allocator.directBuffer(8, 64);
        try {
            assertTrue(buffer.isDirect());
            assertEquals(8, allocator.metric().usedDirectMemory());
            buffer.writeLong(987654321L);
            buffer.capacity(32);
            assertEquals(32, allocator.metric().usedDirectMemory());
            assertEquals(987654321L, buffer.readLong());
        } finally {
            buffer.release();
        }
        assertEquals(0, allocator.metric().usedDirectMemory());
    }
}
