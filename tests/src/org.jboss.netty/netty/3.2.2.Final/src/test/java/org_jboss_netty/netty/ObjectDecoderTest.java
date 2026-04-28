/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_netty.netty;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectDecoderTest {
    @Test
    void decodesFramesProducedByObjectEncoderOutputStream() throws Exception {
        List<String> payload = Arrays.asList("alpha", "beta");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectEncoderOutputStream encoder = new ObjectEncoderOutputStream(outputStream);
        encoder.writeObject(payload);
        encoder.close();

        DecoderEmbedder<Object> decoder = new DecoderEmbedder<Object>(new ObjectDecoder());
        decoder.offer(ChannelBuffers.wrappedBuffer(outputStream.toByteArray()));

        assertThat(decoder.poll()).isEqualTo(payload);
    }
}
