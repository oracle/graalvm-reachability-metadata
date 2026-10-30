/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ClassResolvers;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectDecoder;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;

public class ObjectDecoderTest {
    @Test
    void waitsForTheWholeSerializedFrame() throws Exception {
        byte[] expected = {3, 1, 4, 1, 5};
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes)) {
            output.writeObject(expected);
        }
        EmbeddedChannel decoder = new EmbeddedChannel(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
        ByteBuf frame = Unpooled.wrappedBuffer(bytes.toByteArray());
        try {
            assertFalse(decoder.writeInbound(frame.readRetainedSlice(3)));
            assertTrue(decoder.writeInbound(frame.readRetainedSlice(frame.readableBytes())));
            assertArrayEquals(expected, decoder.<byte[]>readInbound());
        } finally {
            frame.release();
            decoder.finishAndReleaseAll();
        }
    }
}
