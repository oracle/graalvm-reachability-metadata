/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.bootstrap.Bootstrap;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.netty.shaded.io.netty.channel.ChannelFuture;
import io.grpc.netty.shaded.io.netty.channel.ChannelInboundHandlerAdapter;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.InternetProtocolFamily;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;

public class SelectorProviderUtilTest {
    @Test
    void createsAndBindsAnExplicitIpv4Socket() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Channel channel = null;
        try {
            ChannelFuture bound = new Bootstrap().group(group)
                    .channelFactory(() -> new NioSocketChannel(SelectorProvider.provider(), InternetProtocolFamily.IPv4))
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind("127.0.0.1", 0);
            channel = bound.channel();
            assertTrue(bound.await(30, TimeUnit.SECONDS));
            assertTrue(bound.isSuccess(), () -> String.valueOf(bound.cause()));
            assertEquals("127.0.0.1", ((InetSocketAddress) channel.localAddress()).getAddress().getHostAddress());
        } finally {
            if (channel != null) {
                assertTrue(channel.close().await(30, TimeUnit.SECONDS));
            }
            assertTrue(group.shutdownGracefully(0, 30, TimeUnit.SECONDS).await(30, TimeUnit.SECONDS));
        }
    }
}
