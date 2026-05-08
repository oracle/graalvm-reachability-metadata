/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.ClassResolver;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompactObjectInputStreamTest {
    @Test
    void fallsBackToObjectInputStreamResolutionForFatClassDescriptors() throws Exception {
        int[] expected = new int[] {1, 2, 3};
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new ObjectEncoder());
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new ObjectDecoder(new RejectingClassResolver()));
        ByteBuf encoded = null;
        try {
            assertThat(encoderChannel.writeOutbound(expected)).isTrue();
            encoded = (ByteBuf) encoderChannel.readOutbound();

            assertThat(decoderChannel.writeInbound(encoded)).isTrue();
            encoded = null;
            int[] decoded = (int[]) decoderChannel.readInbound();

            assertThat(decoded).containsExactly(expected);
        } finally {
            if (encoded != null) {
                encoded.release();
            }
            encoderChannel.finish();
            decoderChannel.finish();
        }
    }

    private static final class RejectingClassResolver implements ClassResolver {
        @Override
        public Class<?> resolve(String className) throws ClassNotFoundException {
            throw new ClassNotFoundException(className);
        }
    }
}
