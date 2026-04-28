/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AbstractBootstrapInnerBootstrapChannelFactoryTest {
    @Test
    void channelClassIsInstantiatedWhenRegisteringBootstrap() throws Exception {
        LocalEventLoopGroup group = new LocalEventLoopGroup(1);
        Channel channel = null;
        try {
            ChannelFuture registration = new Bootstrap()
                    .group(group)
                    .channel(LocalChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .register()
                    .sync();

            channel = registration.channel();
            Assertions.assertTrue(channel instanceof LocalChannel);
            Assertions.assertTrue(channel.isRegistered());
            Assertions.assertTrue(channel.isOpen());
        } finally {
            if (channel != null) {
                channel.close().sync();
            }
            group.shutdownGracefully(0L, 5L, TimeUnit.SECONDS).sync();
        }
    }
}
