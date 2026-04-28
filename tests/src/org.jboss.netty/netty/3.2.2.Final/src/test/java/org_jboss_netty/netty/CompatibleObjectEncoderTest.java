/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_netty.netty;

import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.jboss.netty.handler.codec.serialization.CompatibleObjectEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompatibleObjectEncoderTest {
    @Test
    void encodesObjectsReadableByStandardObjectInputStream() throws Exception {
        List<String> payload = Arrays.asList("alpha", "beta");
        EncoderEmbedder<ChannelBuffer> encoder = new EncoderEmbedder<ChannelBuffer>(new CompatibleObjectEncoder());

        encoder.offer(payload);
        ChannelBuffer buffer = encoder.poll();

        ObjectInputStream objectInputStream = new ObjectInputStream(new ChannelBufferInputStream(buffer));
        assertThat(objectInputStream.readObject()).isEqualTo(payload);
    }
}
