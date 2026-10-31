/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.ChannelHandlerContext;
import io.grpc.netty.shaded.io.netty.channel.SimpleChannelInboundHandler;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.LineBasedFrameDecoder;
import io.grpc.netty.shaded.io.netty.handler.codec.string.StringDecoder;
import io.grpc.netty.shaded.io.netty.handler.codec.string.StringEncoder;

public class AbstractChannelHandlerContextTest {
    @Test
    void dispatchesDecodedMessagesAndEncodedRepliesThroughThePipeline() {
        EmbeddedChannel channel = new EmbeddedChannel(new LineBasedFrameDecoder(1024),
                new StringDecoder(UTF_8), new StringEncoder(UTF_8), new ReplyHandler());
        try {
            assertFalse(channel.writeInbound(Unpooled.copiedBuffer("registry\n", UTF_8)));
            ByteBuf reply = channel.readOutbound();
            try {
                assertEquals("ack:registry", reply.toString(UTF_8));
            } finally {
                reply.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static final class ReplyHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext context, String message) {
            context.writeAndFlush("ack:" + message);
        }
    }
}
