/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class PooledByteBufAllocatorTest {
    @Test
    void allocatesAndReleasesPooledHeapBuffers() {
        exercisePool(false);
    }

    @Test
    void allocatesAndReleasesPooledDirectBuffers() {
        exercisePool(true);
    }

    @SuppressWarnings("deprecation")
    private void exercisePool(boolean direct) {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(
                direct, 1, 1, 8192, 1, 32, 32, true);
        try {
            ByteBuf buffer = direct ? allocator.directBuffer(32, 1024) : allocator.heapBuffer(32, 1024);
            try {
                assertEquals(direct, buffer.isDirect());
                buffer.writeInt(0x12345678);
                buffer.capacity(512);
                assertEquals(0x12345678, buffer.getInt(0));
                ByteBuf duplicate = buffer.retainedDuplicate();
                try {
                    assertEquals(0x12345678, duplicate.readInt());
                    assertEquals(0, buffer.readerIndex());
                    duplicate.setInt(0, 73);
                    assertEquals(73, buffer.readInt());
                } finally {
                    duplicate.release();
                }
                assertEquals(1, allocator.metric().numHeapArenas());
                assertEquals(1, allocator.metric().numDirectArenas());
            } finally {
                buffer.release();
            }
            assertEquals(0, buffer.refCnt());
            assertEquals(0, allocator.pinnedHeapMemory());
            assertEquals(0, allocator.pinnedDirectMemory());
            assertTrue(allocator.trimCurrentThreadCache());
        } finally {
            allocator.freeThreadLocalCache();
        }
    }
}
