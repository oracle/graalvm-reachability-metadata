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
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.CompatibleObjectEncoder;
import org.junit.jupiter.api.Test;

public class CompatibleObjectEncoderTest {
    @Test
    void writesAStandardSerializedObjectStream() {
        EmbeddedChannel channel = new EmbeddedChannel(new CompatibleObjectEncoder());
        try {
            assertThat(channel.writeOutbound("compatible-pulsar-value")).isTrue();
            ByteBuf encoded = channel.readOutbound();
            try {
                assertThat(encoded.readUnsignedShort()).isEqualTo(0xaced);
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
