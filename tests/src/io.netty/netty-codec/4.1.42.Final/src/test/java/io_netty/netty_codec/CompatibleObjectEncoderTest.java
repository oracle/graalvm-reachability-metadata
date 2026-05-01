/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.CompatibleObjectEncoder;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompatibleObjectEncoderTest {
    @Test
    void writesSerializableMessagesWithStandardObjectOutputStream() throws Exception {
        String expectedMessage = "netty compatible serialization";
        EmbeddedChannel channel = new EmbeddedChannel(new CompatibleObjectEncoder());
        ByteBuf encodedMessage = null;

        try {
            assertTrue(channel.writeOutbound(expectedMessage));
            encodedMessage = channel.readOutbound();
            assertNotNull(encodedMessage);

            Object actualMessage = readObject(encodedMessage);

            assertEquals(expectedMessage, actualMessage);
            assertNull(channel.readOutbound());
        } finally {
            if (encodedMessage != null) {
                encodedMessage.release();
            }
            channel.finishAndReleaseAll();
        }
    }

    private static Object readObject(ByteBuf encodedMessage) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteBufInputStream(encodedMessage))) {
            return input.readObject();
        }
    }
}
