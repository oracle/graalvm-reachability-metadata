/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.security.Provider;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class PlatformTest {
    private static final int NETWORK_TIMEOUT_MILLIS = 10_000;

    @Test
    void socketFactoryWrapsSocketChannelBackedSocket() throws Exception {
        Conscrypt.checkAvailability();
        SSLSocketFactory factory = newSocketFactory();
        Conscrypt.setUseEngineSocket(factory, false);

        try (LoopbackServer server = new LoopbackServer();
                SocketChannel channel = SocketChannel.open()) {
            Socket socket = channel.socket();
            socket.connect(server.address(), NETWORK_TIMEOUT_MILLIS);
            socket.setSoTimeout(NETWORK_TIMEOUT_MILLIS);
            server.accept();

            try (SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                    socket, "localhost", server.port(), false)) {
                assertThat(Conscrypt.isConscrypt(sslSocket)).isTrue();
            } finally {
                socket.close();
            }
        }
    }

    @Test
    void socketFactoryWrapsPlainSocketAndReportsPeerHostOrIp() throws Exception {
        Conscrypt.checkAvailability();
        SSLSocketFactory factory = newSocketFactory();
        Conscrypt.setUseEngineSocket(factory, false);
        InetAddress localhost = InetAddress.getByName("localhost");

        try (LoopbackServer server = new LoopbackServer(localhost);
                Socket socket = new Socket()) {
            socket.connect(server.address(), NETWORK_TIMEOUT_MILLIS);
            socket.setSoTimeout(NETWORK_TIMEOUT_MILLIS);
            server.accept();

            try (SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                    socket, null, server.port(), false)) {
                assertThat(Conscrypt.isConscrypt(sslSocket)).isTrue();
                assertThat(Conscrypt.getHostname(sslSocket)).isNull();
                assertThat(Conscrypt.getHostnameOrIP(sslSocket)).isNotBlank();
            }
        }
    }

    private static SSLSocketFactory newSocketFactory() throws Exception {
        Provider provider = Conscrypt.newProvider();
        SSLContext context = SSLContext.getInstance("TLS", provider);
        context.init(null, null, new SecureRandom());
        assertThat(Conscrypt.isConscrypt(context)).isTrue();
        SSLSocketFactory factory = context.getSocketFactory();
        assertThat(Conscrypt.isConscrypt(factory)).isTrue();
        return factory;
    }

    private static final class LoopbackServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private Socket acceptedSocket;

        private LoopbackServer() throws IOException {
            this(InetAddress.getLoopbackAddress());
        }

        private LoopbackServer(InetAddress bindAddress) throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(NETWORK_TIMEOUT_MILLIS);
            serverSocket.bind(new InetSocketAddress(bindAddress, 0));
        }

        private InetSocketAddress address() {
            return new InetSocketAddress(
                    serverSocket.getInetAddress(), serverSocket.getLocalPort());
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void accept() throws IOException {
            acceptedSocket = serverSocket.accept();
            acceptedSocket.setSoTimeout(NETWORK_TIMEOUT_MILLIS);
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                if (acceptedSocket != null) {
                    acceptedSocket.close();
                }
            } catch (IOException e) {
                failure = e;
            }

            try {
                serverSocket.close();
            } catch (IOException e) {
                if (failure != null) {
                    failure.addSuppressed(e);
                } else {
                    failure = e;
                }
            }

            if (failure != null) {
                throw failure;
            }
        }
    }
}
