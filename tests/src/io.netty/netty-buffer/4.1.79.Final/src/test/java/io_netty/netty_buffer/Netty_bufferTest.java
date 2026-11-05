/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class Netty_bufferTest {
    @Test
    void growsBufferAndReadsBothByteOrders() {
        ByteBuf buffer = Unpooled.buffer(4, 64);
        try {
            buffer.writeInt(0x12345678);
            buffer.writeIntLE(0x10203040);
            assertTrue(buffer.capacity() >= 8);
            assertEquals(8, buffer.readableBytes());
            assertEquals(0x12345678, buffer.readInt());
            assertEquals(0x10203040, buffer.readIntLE());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
        assertEquals(0, buffer.refCnt());
    }

    @Test
    void retainedSliceSharesContentWithIndependentReaderIndex() {
        ByteBuf buffer = Unpooled.buffer(8);
        try {
            buffer.writeInt(17).writeInt(29);
            ByteBuf slice = buffer.retainedSlice(4, 4);
            try {
                assertEquals(2, buffer.refCnt());
                slice.setInt(0, 41);
                assertEquals(41, buffer.getInt(4));
                assertEquals(41, slice.readInt());
                assertEquals(0, buffer.readerIndex());
            } finally {
                slice.release();
            }
            assertEquals(1, buffer.refCnt());
        } finally {
            buffer.release();
        }
    }

    @Test
    void wrappedBytesShareStorageWhileCopiedBytesAreIndependent() {
        byte[] bytes = {10, 20, 30};
        ByteBuf wrapped = Unpooled.wrappedBuffer(bytes);
        try {
            ByteBuf copied = Unpooled.copiedBuffer(bytes);
            try {
                bytes[1] = 42;
                assertEquals(42, wrapped.getByte(1));
                assertEquals(20, copied.getByte(1));
                wrapped.setByte(2, 55);
                assertEquals(55, bytes[2]);
                assertEquals(30, copied.getByte(2));
            } finally {
                copied.release();
            }
        } finally {
            wrapped.release();
        }
    }
}
