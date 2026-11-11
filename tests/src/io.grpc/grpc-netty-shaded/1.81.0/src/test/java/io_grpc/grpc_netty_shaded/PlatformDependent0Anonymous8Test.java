/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;

public class PlatformDependent0Anonymous8Test {
    @Test
    void copiesReadableDirectBufferPayloadIntoIndependentByteArrays() {
        byte[] payload = new byte[65536];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31 + 7);
        }
        ByteBuf buffer = Unpooled.directBuffer(payload.length + 8);
        try {
            buffer.writeInt(123456);
            buffer.writeBytes(payload);
            buffer.writeInt(654321);
            buffer.readerIndex(4);
            buffer.writerIndex(4 + payload.length);

            byte[] first = ByteBufUtil.getBytes(buffer);
            byte[] second = ByteBufUtil.getBytes(buffer);
            assertArrayEquals(payload, first);
            assertArrayEquals(payload, second);
            assertEquals(4, buffer.readerIndex());
            assertEquals(4 + payload.length, buffer.writerIndex());

            first[0] = (byte) (first[0] ^ 0xff);
            assertEquals(payload[0], second[0]);
            assertEquals(payload[0], buffer.getByte(4));
        } finally {
            buffer.release();
        }
    }
}
