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
import com.google.protobuf.nano.MessageNano;

import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.protobuf.ProtobufDecoderNano;

public class ProtobufDecoderNanoTest {
    @Test
    void decodesAnApplicationNanoMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProtobufDecoderNano(Counter.class));
        try {
            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {8, 42})));
            Counter decoded = channel.readInbound();
            assertEquals(42, decoded.value);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    public static final class Counter extends MessageNano {
        private int value;

        public Counter() {
        }

        @Override
        public Counter mergeFrom(CodedInputByteBufferNano input) throws IOException {
            while (true) {
                int tag = input.readTag();
                if (tag == 0) {
                    return this;
                }
                if (tag == 8) {
                    value = input.readInt32();
                } else if (!input.skipField(tag)) {
                    return this;
                }
            }
        }
    }
}
