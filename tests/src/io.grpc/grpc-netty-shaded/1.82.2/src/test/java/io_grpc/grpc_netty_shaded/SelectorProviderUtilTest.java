/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.ChannelFuture;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.InternetProtocolFamily;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;

public class SelectorProviderUtilTest {
    @Test
    void bindsAnExplicitIpv4Channel() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioSocketChannel channel = new NioSocketChannel(SelectorProvider.provider(), InternetProtocolFamily.IPv4);
        try {
            ChannelFuture registration = group.register(channel);
            assertTrue(registration.await(30, TimeUnit.SECONDS));
            assertTrue(registration.isSuccess(), () -> String.valueOf(registration.cause()));
            ChannelFuture bind = channel.bind(new InetSocketAddress("127.0.0.1", 0));
            assertTrue(bind.await(30, TimeUnit.SECONDS));
            assertTrue(bind.isSuccess(), () -> String.valueOf(bind.cause()));
            assertTrue(channel.localAddress().getPort() > 0);
        } finally {
            assertTrue(channel.close().await(30, TimeUnit.SECONDS));
            assertTrue(group.shutdownGracefully(0, 10, TimeUnit.SECONDS).await(30, TimeUnit.SECONDS));
        }
    }
}
