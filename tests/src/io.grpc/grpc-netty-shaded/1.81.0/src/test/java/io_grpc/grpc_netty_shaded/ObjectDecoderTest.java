/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ClassResolvers;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectDecoder;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;

public class ObjectDecoderTest {
    @Test
    void waitsForTheCompleteObjectFrame() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes)) {
            output.writeObject("fragmented message");
        }
        byte[] frame = bytes.toByteArray();
        EmbeddedChannel decoder = new EmbeddedChannel(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
        try {
            assertFalse(decoder.writeInbound(Unpooled.wrappedBuffer(frame, 0, 3)));
            assertTrue(decoder.writeInbound(Unpooled.wrappedBuffer(frame, 3, frame.length - 3)));
            assertEquals("fragmented message", decoder.readInbound());
        } finally {
            decoder.finishAndReleaseAll();
        }
    }
}
