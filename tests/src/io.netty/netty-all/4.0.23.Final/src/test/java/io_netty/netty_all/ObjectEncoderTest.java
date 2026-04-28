/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ObjectEncoderTest {
    @Test
    void serializesOutboundMessageWithLengthPrefix() throws Exception {
        String message = "object-encoder-message";
        EmbeddedChannel channel = new EmbeddedChannel(new ObjectEncoder());
        ByteBuf encoded = null;
        try {
            Assertions.assertTrue(channel.writeOutbound(message));
            encoded = (ByteBuf) channel.readOutbound();
            Assertions.assertNotNull(encoded);

            int readableBytes = encoded.readableBytes();
            Assertions.assertEquals(readableBytes - 4, encoded.getInt(encoded.readerIndex()));

            try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(
                    new ByteBufInputStream(encoded), ObjectEncoderTest.class.getClassLoader())) {
                Assertions.assertEquals(message, input.readObject());
            }
        } finally {
            if (encoded != null) {
                encoded.release();
            }
            channel.finish();
        }
    }
}
