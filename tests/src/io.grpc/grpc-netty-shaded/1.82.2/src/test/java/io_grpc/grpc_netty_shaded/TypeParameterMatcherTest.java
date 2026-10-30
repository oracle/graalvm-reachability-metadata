/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.ChannelHandlerContext;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.MessageToMessageEncoder;

public class TypeParameterMatcherTest {
    @Test
    void resolvesParameterizedArrayMessagesThroughAnEncoder() {
        EmbeddedChannel channel = new EmbeddedChannel(new ListArrayEncoder());
        try {
            List<?>[] input = {List.of("one", "two"), List.of("three")};
            assertTrue(channel.writeOutbound((Object) input));
            assertEquals(3, channel.<Integer>readOutbound());
            assertTrue(channel.writeOutbound("passthrough"));
            assertEquals("passthrough", channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static final class ListArrayEncoder extends MessageToMessageEncoder<List<?>[]> {
        @Override
        protected void encode(ChannelHandlerContext context, List<?>[] message, List<Object> output) {
            int size = 0;
            for (List<?> list : message) {
                size += list.size();
            }
            output.add(size);
        }
    }
}
