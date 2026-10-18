/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import org.apache.pulsar.shade.io.netty.buffer.ByteBuf;
import org.apache.pulsar.shade.io.netty.channel.embedded.EmbeddedChannel;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectDecoder;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ObjectEncoder;
import org.junit.jupiter.api.Test;

public class CompactObjectInputStreamTest {

    @Test
    void resolvesAndReadsAUserDefinedMessageClass() {
        final EmbeddedChannel encoder = new EmbeddedChannel(new ObjectEncoder());
        final EmbeddedChannel decoder = new EmbeddedChannel(
                new ObjectDecoder(ClassResolvers.cacheDisabled(getClass().getClassLoader())));
        try {
            assertThat(encoder.writeOutbound(new Message("orders", 7))).isTrue();
            final ByteBuf encoded = encoder.readOutbound();
            assertThat(decoder.writeInbound(encoded)).isTrue();

            assertThat((Message) decoder.readInbound()).isEqualTo(new Message("orders", 7));
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    public record Message(String topic, int sequence) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
