/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.IOException;
import java.nio.channels.DatagramChannel;

import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NioDatagramChannelConfigTest {
    @Test
    void multicastTimeToLiveCanBeUpdatedThroughNioConfig() throws IOException {
        DatagramChannel javaChannel = DatagramChannel.open();
        NioDatagramChannel channel = new NioDatagramChannel(javaChannel);
        try {
            DatagramChannelConfig config = channel.config();
            int ttl = 7;

            config.setTimeToLive(ttl);

            Assertions.assertEquals(ttl, config.getTimeToLive());
        } finally {
            javaChannel.close();
        }
    }
}
