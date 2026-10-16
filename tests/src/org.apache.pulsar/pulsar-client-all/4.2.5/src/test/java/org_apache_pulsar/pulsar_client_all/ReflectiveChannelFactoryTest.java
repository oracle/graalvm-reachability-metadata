/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.pulsar.shade.io.netty.channel.Channel;
import org.apache.pulsar.shade.io.netty.channel.nio.NioEventLoopGroup;
import org.apache.pulsar.shade.io.netty.channel.ReflectiveChannelFactory;
import org.apache.pulsar.shade.io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

public class ReflectiveChannelFactoryTest {
    @Test
    void constructsChannelsUsingTheirPublicConstructor() throws InterruptedException {
        final ReflectiveChannelFactory<NioSocketChannel> factory =
                new ReflectiveChannelFactory<>(NioSocketChannel.class);
        final Channel channel = factory.newChannel();
        final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);
        try {
            assertThat(channel).isInstanceOf(NioSocketChannel.class);
            assertThat(eventLoop.register(channel).await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(channel.isRegistered()).isTrue();
        } finally {
            try {
                if (channel.isRegistered()) {
                    assertThat(channel.close().await(10, TimeUnit.SECONDS)).isTrue();
                }
            } finally {
                assertThat(eventLoop.shutdownGracefully(0, 10, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS))
                        .isTrue();
            }
        }
    }
}
