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

import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ClassResolvers;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectDecoder;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectEncoder;

public class ClassLoaderClassResolverTest {
    @Test
    void resolvesArrayDescriptorsWhenDecodingObjects() {
        EmbeddedChannel encoder = new EmbeddedChannel(new ObjectEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new ObjectDecoder(
                ClassResolvers.cacheDisabled(getClass().getClassLoader())));
        try {
            assertTrue(encoder.writeOutbound((Object) new long[] {11, 23, 47}));
            assertTrue(decoder.writeInbound((Object) encoder.readOutbound()));
            assertArrayEquals(new long[] {11, 23, 47}, decoder.<long[]>readInbound());
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
