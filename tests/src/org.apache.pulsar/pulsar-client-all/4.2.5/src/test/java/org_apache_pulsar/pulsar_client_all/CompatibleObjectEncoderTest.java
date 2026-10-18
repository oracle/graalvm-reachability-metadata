/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectInputStream;
import org.apache.pulsar.shade.io.netty.buffer.ByteBuf;
import org.apache.pulsar.shade.io.netty.buffer.ByteBufInputStream;
import org.apache.pulsar.shade.io.netty.channel.embedded.EmbeddedChannel;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.CompatibleObjectEncoder;
import org.junit.jupiter.api.Test;

public class CompatibleObjectEncoderTest {

    @Test
    @SuppressWarnings("deprecation")
    void writesAStandardJavaSerializationStream() throws Exception {
        final EmbeddedChannel encoder = new EmbeddedChannel(new CompatibleObjectEncoder());
        try {
            assertThat(encoder.writeOutbound("pulsar-message")).isTrue();
            final ByteBuf encoded = encoder.readOutbound();
            try (ObjectInputStream input = new ObjectInputStream(new ByteBufInputStream(encoded, true))) {
                assertThat(input.readObject()).isEqualTo("pulsar-message");
            }
        } finally {
            encoder.finishAndReleaseAll();
        }
    }
}
