/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ObjectInputStream;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufInputStream;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.CompatibleObjectEncoder;

public class CompatibleObjectEncoderTest {
    @Test
    void interoperatesWithStandardJavaObjectStreams() throws Exception {
        EmbeddedChannel encoder = new EmbeddedChannel(new CompatibleObjectEncoder(1));
        try {
            assertTrue(encoder.writeOutbound((Object) new int[] {2, 3, 5, 7}));
            ByteBuf encoded = encoder.readOutbound();
            try (ObjectInputStream input = new ObjectInputStream(new ByteBufInputStream(encoded, true))) {
                assertArrayEquals(new int[] {2, 3, 5, 7}, (int[]) input.readObject());
            }
        } finally {
            encoder.finishAndReleaseAll();
        }
    }
}
