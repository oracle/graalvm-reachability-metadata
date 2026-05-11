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
import io.netty.handler.codec.serialization.CompatibleObjectEncoder;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class CompatibleObjectEncoderTest {
    @Test
    void writesSerializableObjectToStandardObjectStream() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new CompatibleObjectEncoder());
        ByteBuf encoded = null;
        try {
            assertThat(channel.writeOutbound("netty-compatible-serialization")).isTrue();
            encoded = (ByteBuf) channel.readOutbound();

            try (ObjectInputStream in = new ObjectInputStream(new ByteBufInputStream(encoded))) {
                assertThat(in.readObject()).isEqualTo("netty-compatible-serialization");
            }
        } finally {
            if (encoded != null) {
                encoded.release();
            }
            channel.finish();
        }
    }
}
