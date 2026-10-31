/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.ChannelFuture;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;

import io.grpc.netty.shaded.io.netty.channel.socket.DatagramChannelConfig;
import io.grpc.netty.shaded.io.netty.channel.socket.InternetProtocolFamily;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioDatagramChannel;

public class NioDatagramChannelConfigTest {
    @Test
    void configuresMulticastSocketOptions() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioDatagramChannel channel = new NioDatagramChannel(InternetProtocolFamily.IPv4);
        try {
            ChannelFuture registration = group.register(channel);
            assertTrue(registration.await(30, TimeUnit.SECONDS));
            assertTrue(registration.isSuccess(), () -> String.valueOf(registration.cause()));
            DatagramChannelConfig config = channel.config();
            config.setTimeToLive(7);
            assertEquals(7, config.getTimeToLive());
            config.setLoopbackModeDisabled(true);
            assertTrue(config.isLoopbackModeDisabled());
            config.setLoopbackModeDisabled(false);
            assertEquals(false, config.isLoopbackModeDisabled());
        } finally {
            assertTrue(channel.close().await(30, TimeUnit.SECONDS));
            assertTrue(group.shutdownGracefully(0, 10, TimeUnit.SECONDS).await(30, TimeUnit.SECONDS));
        }
    }
}
