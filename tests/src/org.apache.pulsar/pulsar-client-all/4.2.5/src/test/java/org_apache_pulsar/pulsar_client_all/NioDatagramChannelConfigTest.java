/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.DatagramChannel;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.shade.io.netty.channel.nio.NioEventLoopGroup;
import org.apache.pulsar.shade.io.netty.channel.socket.DatagramChannelConfig;
import org.apache.pulsar.shade.io.netty.channel.socket.nio.NioDatagramChannel;
import org.apache.pulsar.shade.io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class NioDatagramChannelConfigTest {
    @Test
    void readsAndWritesMulticastSocketOptions() throws Exception {
        DatagramChannel socket = DatagramChannel.open();
        NioDatagramChannel channel = new NioDatagramChannel(socket);
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        try {
            awaitSuccess(eventLoopGroup.register(channel), 10);
            DatagramChannelConfig config = channel.config();
            config.setTimeToLive(8);
            config.setLoopbackModeDisabled(false);

            assertThat(config.getTimeToLive()).isEqualTo(8);
            assertThat(config.isLoopbackModeDisabled()).isFalse();
        } finally {
            try {
                if (channel.isRegistered()) {
                    awaitSuccess(channel.close(), 10);
                }
            } finally {
                socket.close();
                awaitSuccess(eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS), 20);
            }
        }
        assertThat(channel.isOpen()).isFalse();
    }

    private static void awaitSuccess(Future<?> future, long timeoutSeconds) {
        assertThat(future.awaitUninterruptibly(timeoutSeconds, TimeUnit.SECONDS)).isTrue();
        assertThat(future.isSuccess()).isTrue();
    }
}
