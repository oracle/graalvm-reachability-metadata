/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.socket.DatagramChannelConfig;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioDatagramChannel;

public class NioDatagramChannelConfigTest {
    @Test
    void configuresMulticastOptionsThroughTheNettyDatagramApi() throws Exception {
        try (DatagramChannel socket = DatagramChannel.open(StandardProtocolFamily.INET)) {
            NioDatagramChannel channel = new NioDatagramChannel(socket);
            DatagramChannelConfig config = channel.config();
            config.setTimeToLive(7);
            assertEquals(7, config.getTimeToLive());
            config.setLoopbackModeDisabled(true);
            assertTrue(config.isLoopbackModeDisabled());
            config.setLoopbackModeDisabled(false);
            assertFalse(config.isLoopbackModeDisabled());
            NetworkInterface loopback = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));
            config.setNetworkInterface(loopback);
            assertEquals(loopback, config.getNetworkInterface());
        }
    }
}
