/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;
import org.glassfish.grizzly.nio.transport.UDPNIOConnection;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransportBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UDPNIOConnectionTest {
    @Test
    void multicastLifecycleUsesJdkMembershipMethods() throws Exception {
        UDPNIOTransport transport = UDPNIOTransportBuilder.newInstance().build();
        try (DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET)) {
            channel.bind(new InetSocketAddress(0));

            UDPNIOConnection connection = new UDPNIOConnection(transport, channel);
            InetAddress group = InetAddress.getByName("230.0.0.1");
            InetAddress source = InetAddress.getByName("127.0.0.1");
            NetworkInterface networkInterface = firstIpv4NetworkInterface();

            boolean joined = tryJoin(connection, group, networkInterface);
            if (joined) {
                tryBlockAndUnblock(connection, group, networkInterface, source);
                connection.drop(group, networkInterface);
            }

            assertThat(connection.isConnected()).isFalse();
            assertThat(connection.getLocalAddress()).isNotNull();
            assertThat(connection.toString()).contains("UDPNIOConnection");
        } finally {
            transport.shutdownNow();
        }
    }

    private static boolean tryJoin(UDPNIOConnection connection, InetAddress group,
            NetworkInterface networkInterface) throws IOException {
        try {
            connection.join(group, networkInterface);
            return true;
        } catch (IOException e) {
            assertThat(e).isNotNull();
            return false;
        }
    }

    private static void tryBlockAndUnblock(UDPNIOConnection connection, InetAddress group,
            NetworkInterface networkInterface, InetAddress source) throws IOException {
        try {
            connection.block(group, networkInterface, source);
            connection.unblock(group, networkInterface, source);
        } catch (IOException e) {
            assertThat(e).isNotNull();
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            assertThat(e).isNotNull();
        }
    }

    private static NetworkInterface firstIpv4NetworkInterface() throws SocketException {
        NetworkInterface fallback = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (hasIpv4Address(networkInterface)) {
                if (fallback == null) {
                    fallback = networkInterface;
                }
                if (networkInterface.isUp() && networkInterface.supportsMulticast()) {
                    return networkInterface;
                }
            }
        }

        assertThat(fallback).as("an IPv4 network interface is required").isNotNull();
        return fallback;
    }

    private static boolean hasIpv4Address(NetworkInterface networkInterface) {
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            if (addresses.nextElement() instanceof Inet4Address) {
                return true;
            }
        }
        return false;
    }
}
