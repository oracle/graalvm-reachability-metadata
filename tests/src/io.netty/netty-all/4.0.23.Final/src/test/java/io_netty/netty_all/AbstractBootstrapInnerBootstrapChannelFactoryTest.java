/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractBootstrapInnerBootstrapChannelFactoryTest {
    @Test
    void createsChannelByClassWhenBootstrapRegisters() throws Exception {
        LocalEventLoopGroup group = new LocalEventLoopGroup(1);
        Channel channel = null;
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(LocalChannel.class)
                    .handler(new ChannelInboundHandlerAdapter());

            ChannelFuture registration = bootstrap.register();
            assertThat(registration.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(registration.cause()).isNull();

            channel = registration.channel();
            assertThat(channel).isInstanceOf(LocalChannel.class);
            assertThat(channel.isRegistered()).isTrue();
        } finally {
            if (channel != null) {
                assertThat(channel.close().await(5, TimeUnit.SECONDS)).isTrue();
            }
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            assertThat(group.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
