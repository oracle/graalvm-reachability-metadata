/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectEncoderTest {
    @Test
    void writesLengthPrefixedObjectReadableByObjectDecoder() {
        String expectedMessage = "netty object encoder message";
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new ObjectEncoder());
        ByteBuf encodedMessage = null;

        try {
            assertTrue(encoderChannel.writeOutbound(expectedMessage));
            encodedMessage = encoderChannel.readOutbound();
            assertNotNull(encodedMessage);
            assertEquals(encodedMessage.readableBytes() - Integer.BYTES, encodedMessage.getInt(0));

            Object actualMessage = decodeObject(encodedMessage.retainedDuplicate());

            assertEquals(expectedMessage, actualMessage);
            assertNull(encoderChannel.readOutbound());
        } finally {
            if (encodedMessage != null) {
                encodedMessage.release();
            }
            encoderChannel.finishAndReleaseAll();
        }
    }

    private static Object decodeObject(ByteBuf encodedMessage) {
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new ObjectDecoder(
                ClassResolvers.cacheDisabled(ObjectEncoderTest.class.getClassLoader())));
        try {
            assertTrue(decoderChannel.writeInbound(encodedMessage));
            Object decodedMessage = decoderChannel.readInbound();

            assertNull(decoderChannel.readInbound());
            return decodedMessage;
        } finally {
            decoderChannel.finishAndReleaseAll();
        }
    }
}
