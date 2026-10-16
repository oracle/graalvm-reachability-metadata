/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.io.netty.channel.Channel;
import org.apache.pulsar.shade.io.netty.channel.ReflectiveChannelFactory;
import org.apache.pulsar.shade.io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

public class ReflectiveChannelFactoryTest {
    @Test
    void constructsChannelsUsingTheirPublicConstructor() {
        final ReflectiveChannelFactory<NioSocketChannel> factory =
                new ReflectiveChannelFactory<>(NioSocketChannel.class);
        final Channel channel = factory.newChannel();
        try {
            assertThat(channel).isInstanceOf(NioSocketChannel.class);
            assertThat(channel.isOpen()).isTrue();
        } finally {
            channel.close().syncUninterruptibly();
        }
    }
}
