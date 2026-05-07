/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.netty_nio_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.nio.netty.internal.utils.ChannelResolver;

public class ChannelResolverTest {
    @Test
    void resolveSocketChannelFactoryLoadsMappedOioSocketChannel() {
        EventLoopGroup eventLoopGroup = new OioEventLoopGroup(1);

        try {
            ChannelFactory<? extends Channel> factory = ChannelResolver.resolveSocketChannelFactory(eventLoopGroup);

            assertThat(factory).hasToString("ReflectiveChannelFactory(OioSocketChannel.class)");
        } finally {
            shutdown(eventLoopGroup);
        }
    }

    @Test
    void resolveDatagramChannelFactoryLoadsMappedOioDatagramChannel() {
        EventLoopGroup eventLoopGroup = new OioEventLoopGroup(1);

        try {
            ChannelFactory<? extends DatagramChannel> factory =
                ChannelResolver.resolveDatagramChannelFactory(eventLoopGroup);

            assertThat(factory).hasToString("ReflectiveChannelFactory(OioDatagramChannel.class)");
        } finally {
            shutdown(eventLoopGroup);
        }
    }

    private static void shutdown(EventLoopGroup eventLoopGroup) {
        boolean terminated = eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
                                           .awaitUninterruptibly(5, TimeUnit.SECONDS);

        assertThat(terminated).isTrue();
    }
}
