/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.Test;

import java.nio.channels.DatagramChannel;

import static org.assertj.core.api.Assertions.assertThat;

public class NioDatagramChannelConfigTest {
    @Test
    void readsAndWritesMulticastTimeToLive() throws Exception {
        try (DatagramChannel datagramChannel = DatagramChannel.open()) {
            NioDatagramChannel channel = new NioDatagramChannel(datagramChannel);
            DatagramChannelConfig config = channel.config();

            config.setTimeToLive(1);

            assertThat(config.getTimeToLive()).isEqualTo(1);
        }
    }
}
