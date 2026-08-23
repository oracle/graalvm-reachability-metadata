/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NioDatagramChannelConfigTest {
    @Test
    void readsAndWritesMulticastTimeToLive() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        Channel channel = null;
        try {
            channel = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(0)
                    .sync()
                    .channel();
            DatagramChannelConfig config = ((NioDatagramChannel) channel).config();

            config.setTimeToLive(1);

            assertThat(config.getTimeToLive()).isEqualTo(1);
        } finally {
            if (channel != null) {
                channel.close().sync();
            }
            group.shutdownGracefully().sync();
        }
    }
}
