/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.ChannelHandlerContext;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.MessageToMessageEncoder;

public class TypeParameterMatcherTest {
    @Test
    @SuppressWarnings("unchecked")
    void matchesGenericArrayMessagesInAnOutboundEncoder() {
        EmbeddedChannel channel = new EmbeddedChannel(new ListArrayEncoder());
        try {
            List<String>[] lists = (List<String>[]) new List<?>[]{List.of("first"), List.of("second", "third")};
            assertTrue(channel.writeOutbound((Object) lists));
            assertEquals("first,second,third", channel.readOutbound());
            Object unrelated = new Object();
            assertTrue(channel.writeOutbound(unrelated));
            assertSame(unrelated, channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    public static final class ListArrayEncoder extends MessageToMessageEncoder<List<String>[]> {
        @Override
        protected void encode(ChannelHandlerContext context, List<String>[] message, List<Object> output) {
            StringBuilder result = new StringBuilder();
            for (List<String> list : message) {
                for (String value : list) {
                    if (!result.isEmpty()) {
                        result.append(',');
                    }
                    result.append(value);
                }
            }
            output.add(result.toString());
        }
    }
}
