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

public class ObjectEncoderTest {
    @Test
    void encodesObjectsReadableByObjectDecoderInputStream() throws Exception {
        List<String> payload = Arrays.asList("alpha", "beta");
        EncoderEmbedder<ChannelBuffer> encoder = new EncoderEmbedder<ChannelBuffer>(new ObjectEncoder());

        encoder.offer(payload);
        ChannelBuffer buffer = encoder.poll();

        ObjectDecoderInputStream decoder = new ObjectDecoderInputStream(new ChannelBufferInputStream(buffer));
        assertThat(decoder.readObject()).isEqualTo(payload);
    }
}
