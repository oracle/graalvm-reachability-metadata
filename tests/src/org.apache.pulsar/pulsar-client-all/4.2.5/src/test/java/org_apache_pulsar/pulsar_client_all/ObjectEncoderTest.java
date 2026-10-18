/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.io.netty.buffer.ByteBuf;
import org.apache.pulsar.shade.io.netty.channel.embedded.EmbeddedChannel;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectEncoder;
import org.junit.jupiter.api.Test;

public class ObjectEncoderTest {

    @Test
    void encodesSerializableMessagesWithALengthPrefix() {
        final EmbeddedChannel channel = new EmbeddedChannel(new ObjectEncoder());
        try {
            assertThat(channel.writeOutbound("pulsar-message")).isTrue();
            final ByteBuf encoded = channel.readOutbound();
            try {
                assertThat(encoded.readableBytes()).isGreaterThan(4);
                assertThat(encoded.getInt(0)).isEqualTo(encoded.readableBytes() - 4);
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
