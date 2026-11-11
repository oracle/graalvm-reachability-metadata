/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufInputStream;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectEncoder;

public class ObjectEncoderTest {
    @Test
    void outboundObjectsUseTheLengthPrefixedCompactFormat() throws Exception {
        EmbeddedChannel encoder = new EmbeddedChannel(new ObjectEncoder());
        try {
            assertTrue(encoder.writeOutbound(new int[]{7, 11, 42}));
            ByteBuf frame = encoder.readOutbound();
            try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(new ByteBufInputStream(frame, true))) {
                assertArrayEquals(new int[]{7, 11, 42}, (int[]) input.readObject());
            }
        } finally {
            encoder.finishAndReleaseAll();
        }
    }
}
