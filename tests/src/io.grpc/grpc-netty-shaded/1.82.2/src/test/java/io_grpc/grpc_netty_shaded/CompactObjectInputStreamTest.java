/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ClassResolvers;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectDecoder;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectEncoder;

public class CompactObjectInputStreamTest {
    @Test
    void resolvesPrimitiveTypeDescriptorsInAnObjectStream() {
        EmbeddedChannel encoder = new EmbeddedChannel(new ObjectEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
        try {
            assertTrue(encoder.writeOutbound(int.class));
            assertTrue(decoder.writeInbound((Object) encoder.readOutbound()));
            assertEquals(int.class, decoder.readInbound());
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
