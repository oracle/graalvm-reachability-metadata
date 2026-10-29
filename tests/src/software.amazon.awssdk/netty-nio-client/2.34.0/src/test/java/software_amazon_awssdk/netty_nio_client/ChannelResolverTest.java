/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.netty_nio_client;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.oio.OioEventLoopGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChannelResolverTest {
    @Test
    @Timeout(120)
    void resolvesFactoriesForCustomEventLoopAndSendsTcpAndUdpMessages() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        OioEventLoopGroup eventLoopGroup = new OioEventLoopGroup(2);
        Channel socketChannel = null;
        Channel datagramChannel = null;
        try (ServerSocket tcpServer = new ServerSocket(0, 1, loopback);
                DatagramSocket udpServer = new DatagramSocket(new InetSocketAddress(loopback, 0))) {
            tcpServer.setSoTimeout(30000);
            udpServer.setSoTimeout(30000);
            SdkEventLoopGroup sdkGroup = SdkEventLoopGroup.create(eventLoopGroup);
            assertSame(eventLoopGroup, sdkGroup.eventLoopGroup());

            socketChannel = sdkGroup.channelFactory().newChannel();
            socketChannel.config().setConnectTimeoutMillis(30000);
            awaitSuccess(eventLoopGroup.register(socketChannel));
            awaitSuccess(socketChannel.connect(tcpServer.getLocalSocketAddress()));
            byte[] tcpPayload = "sdk socket factory".getBytes(StandardCharsets.UTF_8);
            try (Socket accepted = tcpServer.accept()) {
                accepted.setSoTimeout(30000);
                awaitSuccess(socketChannel.writeAndFlush(Unpooled.wrappedBuffer(tcpPayload)));
                assertArrayEquals(tcpPayload, accepted.getInputStream().readNBytes(tcpPayload.length));
                assertTrue(socketChannel.isActive());
                assertEquals(tcpServer.getLocalSocketAddress(), socketChannel.remoteAddress());
            }

            datagramChannel = sdkGroup.datagramChannelFactory().newChannel();
            awaitSuccess(eventLoopGroup.register(datagramChannel));
            awaitSuccess(datagramChannel.bind(new InetSocketAddress(loopback, 0)));
            awaitSuccess(datagramChannel.connect(udpServer.getLocalSocketAddress()));
            byte[] udpPayload = "sdk datagram factory".getBytes(StandardCharsets.UTF_8);
            awaitSuccess(datagramChannel.writeAndFlush(Unpooled.wrappedBuffer(udpPayload)));
            DatagramPacket received = new DatagramPacket(new byte[udpPayload.length], udpPayload.length);
            udpServer.receive(received);
            assertEquals(udpPayload.length, received.getLength());
            assertArrayEquals(udpPayload, received.getData());
            assertEquals(datagramChannel.localAddress(), received.getSocketAddress());
            assertTrue(datagramChannel.isActive());
        } finally {
            try {
                if (socketChannel != null) {
                    awaitSuccess(socketChannel.close());
                }
            } finally {
                try {
                    if (datagramChannel != null) {
                        awaitSuccess(datagramChannel.close());
                    }
                } finally {
                    eventLoopGroup.shutdownGracefully(0, 30, TimeUnit.SECONDS).get(30, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static void awaitSuccess(ChannelFuture future) throws Exception {
        future.get(30, TimeUnit.SECONDS);
    }
}
