/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.apache.pulsar.shade.io.netty.channel.nio.NioEventLoopGroup;
import org.apache.pulsar.shade.io.netty.channel.socket.DatagramChannelConfig;
import org.apache.pulsar.shade.io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.Test;

public class NioDatagramChannelConfigTest {
    @Test
    void readsAndWritesNioMulticastOptions() {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        NioDatagramChannel channel = new NioDatagramChannel();
        eventLoopGroup.register(channel).syncUninterruptibly();
        try {
            DatagramChannelConfig config = channel.config();
            config.setTimeToLive(2);
            config.setLoopbackModeDisabled(false);

            assertThat(config.getTimeToLive()).isEqualTo(2);
            assertThat(config.isLoopbackModeDisabled()).isFalse();
        } finally {
            channel.close().syncUninterruptibly();
            eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
