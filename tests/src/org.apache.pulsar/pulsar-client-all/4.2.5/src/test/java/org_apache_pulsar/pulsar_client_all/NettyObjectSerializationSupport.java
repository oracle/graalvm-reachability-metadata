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

public final class NettyObjectSerializationSupport {
    private NettyObjectSerializationSupport() {
    }

    public static void roundTrip() {
        EmbeddedChannel encoder = new EmbeddedChannel(new ObjectEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(
                new ObjectDecoder(ClassResolvers.cacheDisabled(
                        NettyObjectSerializationSupport.class.getClassLoader())));
        try {
            assertThat(encoder.writeOutbound("serialized-pulsar-value")).isTrue();
            ByteBuf encoded = encoder.readOutbound();
            assertThat(decoder.writeInbound(encoded)).isTrue();
            assertThat((String) decoder.readInbound()).isEqualTo("serialized-pulsar-value");
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
