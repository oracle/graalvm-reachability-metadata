/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.ObjectInputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.CompatibleObjectEncoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CompatibleObjectEncoderTest {
    @Test
    void writesSerializableMessageToStandardObjectStream() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new CompatibleObjectEncoder(0));
        ByteBuf encoded = null;
        try {
            Assertions.assertTrue(channel.writeOutbound("netty-compatible-serialization"));
            encoded = channel.readOutbound();
            Assertions.assertNotNull(encoded);

            try (ObjectInputStream input = new ObjectInputStream(new ByteBufInputStream(encoded))) {
                Assertions.assertEquals("netty-compatible-serialization", input.readObject());
            }
        } finally {
            if (encoded != null) {
                encoded.release();
            }
            channel.finish();
        }
    }
}
