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
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectDecoder;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectEncoder;
import org.junit.jupiter.api.Test;

public class ObjectDecoderTest {

    @Test
    void decodesAFramedSerializedMessage() {
        final EmbeddedChannel encoder = new EmbeddedChannel(new ObjectEncoder());
        final EmbeddedChannel decoder = new EmbeddedChannel(
                new ObjectDecoder(ClassResolvers.cacheDisabled(getClass().getClassLoader())));
        try {
            assertThat(encoder.writeOutbound("pulsar-message")).isTrue();
            final ByteBuf encoded = encoder.readOutbound();
            assertThat(decoder.writeInbound(encoded)).isTrue();

            assertThat((String) decoder.readInbound()).isEqualTo("pulsar-message");
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
