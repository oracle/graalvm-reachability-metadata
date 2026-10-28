/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.pulsar.shade.io.netty.buffer.ByteBuf;
import org.apache.pulsar.shade.io.netty.buffer.Unpooled;
import org.apache.pulsar.shade.io.netty.channel.embedded.EmbeddedChannel;
import org.apache.pulsar.shade.io.netty.handler.codec.compression.FastLzFrameEncoder;

public final class ByteBufChecksumSupport {
    private ByteBufChecksumSupport() {
    }

    public static void encode(boolean direct) {
        EmbeddedChannel channel = new EmbeddedChannel(new FastLzFrameEncoder(true));
        ByteBuf input = direct ? Unpooled.directBuffer() : Unpooled.buffer();
        input.writeCharSequence("checksum-payload", StandardCharsets.UTF_8);
        try {
            assertThat(channel.writeOutbound(input)).isTrue();
            ByteBuf encoded = channel.readOutbound();
            try {
                assertThat(encoded.readableBytes()).isGreaterThan("checksum-payload".length());
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
