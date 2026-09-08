/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtobufDecoderTest {
    @Test
    void decodesProtocolBufferMessage() {
        FileDescriptorProto expected = FileDescriptorProto.newBuilder().setName("netty-codec.proto").build();
        ByteBuf input = Unpooled.wrappedBuffer(expected.toByteArray());
        EmbeddedChannel channel = new EmbeddedChannel(new ProtobufDecoder(FileDescriptorProto.getDefaultInstance()));

        assertTrue(channel.writeInbound(input));
        Object decoded = channel.readInbound();

        assertEquals(expected, decoded);
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }
}
