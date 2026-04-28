/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_netty.netty;

import java.util.Arrays;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.jboss.netty.handler.codec.serialization.ObjectDecoderInputStream;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractCodecEmbedderTest {
    @Test
    void pollAllCreatesATypedArrayLargeEnoughForAllProducts() throws Exception {
        EncoderEmbedder<ChannelBuffer> embedder = new EncoderEmbedder<ChannelBuffer>(new ObjectEncoder());
        List<String> first = Arrays.asList("alpha");
        List<String> second = Arrays.asList("beta");

        embedder.offer(first);
        embedder.offer(second);

        ChannelBuffer[] buffers = embedder.pollAll(new ChannelBuffer[0]);

        assertThat(buffers).hasSize(2);
        assertThat(readObject(buffers[0])).isEqualTo(first);
        assertThat(readObject(buffers[1])).isEqualTo(second);
    }

    private static Object readObject(ChannelBuffer buffer) throws Exception {
        return new ObjectDecoderInputStream(new ChannelBufferInputStream(buffer)).readObject();
    }
}
