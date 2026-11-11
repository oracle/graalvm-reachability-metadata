/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import com.google.protobuf.nano.WireFormatNano;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.protobuf.ProtobufDecoderNano;
import io.grpc.netty.shaded.io.netty.handler.codec.protobuf.ProtobufEncoderNano;

public class ProtobufDecoderNanoTest {
    @Test
    void decodesNanoMessagesFromHeapAndDirectBuffers() {
        EmbeddedChannel encoder = new EmbeddedChannel(new ProtobufEncoderNano());
        EmbeddedChannel decoder = new EmbeddedChannel(new ProtobufDecoderNano(Greeting.class));
        try {
            Greeting message = new Greeting();
            message.text = "nano payload";
            message.sequence = 42;
            assertTrue(encoder.writeOutbound(message));
            ByteBuf encoded = encoder.readOutbound();
            try {
                assertTrue(decoder.writeInbound(encoded.retainedDuplicate()));
                assertGreeting(decoder.readInbound());
                ByteBuf direct = Unpooled.directBuffer(encoded.readableBytes());
                direct.writeBytes(encoded, encoded.readerIndex(), encoded.readableBytes());
                assertTrue(decoder.writeInbound(direct));
                assertGreeting(decoder.readInbound());
            } finally {
                encoded.release();
            }
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    private static void assertGreeting(Greeting message) {
        assertEquals("nano payload", message.text);
        assertEquals(42, message.sequence);
    }

    public static final class Greeting extends MessageNano {
        public String text = "";
        public int sequence;

        public Greeting() {
        }

        @Override
        public void writeTo(CodedOutputByteBufferNano output) throws IOException {
            output.writeString(1, text);
            output.writeInt32(2, sequence);
        }

        @Override
        protected int computeSerializedSize() {
            return CodedOutputByteBufferNano.computeStringSize(1, text)
                    + CodedOutputByteBufferNano.computeInt32Size(2, sequence);
        }

        @Override
        public Greeting mergeFrom(CodedInputByteBufferNano input) throws IOException {
            while (true) {
                int tag = input.readTag();
                switch (tag) {
                    case 0:
                        return this;
                    case 10:
                        text = input.readString();
                        break;
                    case 16:
                        sequence = input.readInt32();
                        break;
                    default:
                        if (!WireFormatNano.parseUnknownField(input, tag)) {
                            return this;
                        }
                }
            }
        }
    }
}
