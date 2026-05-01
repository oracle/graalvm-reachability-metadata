/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import java.io.IOException;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoderNano;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtobufDecoderNanoTest {
    @Test
    void decodesNanoMessageUsingPublicNoArgsConstructor() {
        byte[] encoded = MessageNano.toByteArray(new EmptyNanoMessage());
        ByteBuf input = Unpooled.wrappedBuffer(encoded);
        EmbeddedChannel channel = new EmbeddedChannel(new ProtobufDecoderNano(EmptyNanoMessage.class));

        assertTrue(channel.writeInbound(input));
        Object decoded = channel.readInbound();

        assertTrue(decoded instanceof EmptyNanoMessage);
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    public static final class EmptyNanoMessage extends MessageNano {
        public EmptyNanoMessage() {
        }

        @Override
        public EmptyNanoMessage mergeFrom(CodedInputByteBufferNano input) throws IOException {
            return this;
        }
    }
}
