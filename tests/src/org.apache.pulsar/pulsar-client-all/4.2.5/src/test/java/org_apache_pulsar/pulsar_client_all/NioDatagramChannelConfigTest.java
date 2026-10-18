/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.io.netty.channel.nio.NioEventLoopGroup;
import org.apache.pulsar.shade.io.netty.channel.socket.DatagramChannelConfig;
import org.apache.pulsar.shade.io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.Test;

public class NioDatagramChannelConfigTest {

    @Test
    void readsAndWritesStandardMulticastSocketOptions() {
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        final NioDatagramChannel channel = new NioDatagramChannel();
        try {
            eventLoopGroup.register(channel).syncUninterruptibly();
            final DatagramChannelConfig config = channel.config();
            config.setTimeToLive(8);
            config.setLoopbackModeDisabled(false);

            assertThat(config.getTimeToLive()).isEqualTo(8);
            assertThat(config.isLoopbackModeDisabled()).isFalse();
        } finally {
            channel.close().syncUninterruptibly();
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }
}
