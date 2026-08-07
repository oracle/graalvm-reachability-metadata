/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NioDatagramChannelConfigTest {

    @Test
    public void configuresMulticastTimeToLiveThroughDatagramChannelConfig() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        Channel channel = null;
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        protected void initChannel(DatagramChannel ch) {
                        }
                    });

            ChannelFuture bindFuture = bootstrap.bind(0);
            Assertions.assertTrue(bindFuture.await(10, TimeUnit.SECONDS), "Timed out binding UDP channel");
            Assertions.assertTrue(bindFuture.isSuccess(), () -> "Failed binding UDP channel: " + bindFuture.cause());
            channel = bindFuture.channel();

            DatagramChannelConfig config = ((DatagramChannel) channel).config();
            config.setTimeToLive(4);

            Assertions.assertEquals(4, config.getTimeToLive());
        } finally {
            if (channel != null) {
                ChannelFuture closeFuture = channel.close();
                Assertions.assertTrue(closeFuture.await(10, TimeUnit.SECONDS), "Timed out closing UDP channel");
            }
            group.shutdownGracefully().await(10, TimeUnit.SECONDS);
        }
    }
}
