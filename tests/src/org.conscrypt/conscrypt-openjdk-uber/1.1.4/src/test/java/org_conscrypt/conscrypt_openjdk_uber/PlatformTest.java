/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.security.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class PlatformTest {
    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

    @Test
    void layeredSocketFactoryChecksFileDescriptorFromPlainSocketImpl() throws Exception {
        SSLSocketFactory factory = newSocketFactory(false);

        try (ServerSocket serverSocket = newLoopbackServerSocket()) {
            try (AcceptedConnection acceptedConnection = acceptAsync(serverSocket);
                    Socket plainSocket = new Socket(
                            InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
                    SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                            plainSocket, "localhost", serverSocket.getLocalPort(), true);
                    Socket ignored = acceptedConnection.get()) {
                assertThat(Conscrypt.isConscrypt(sslSocket)).isTrue();
                assertThat(sslSocket.isConnected()).isTrue();
            }
        }
    }

    @Test
    void layeredSocketFactoryChecksFileDescriptorFromSocketChannel() throws Exception {
        SSLSocketFactory factory = newSocketFactory(false);

        try (ServerSocket serverSocket = newLoopbackServerSocket()) {
            try (AcceptedConnection acceptedConnection = acceptAsync(serverSocket);
                    SocketChannel channel = SocketChannel.open(
                            new InetSocketAddress(
                                    InetAddress.getLoopbackAddress(),
                                    serverSocket.getLocalPort()));
                    SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                            channel.socket(), "localhost", serverSocket.getLocalPort(), true);
                    Socket ignored = acceptedConnection.get()) {
                assertThat(Conscrypt.isConscrypt(sslSocket)).isTrue();
                assertThat(sslSocket.isConnected()).isTrue();
            }
        }
    }

    @Test
    void getHostnameOrIpUsesOriginalInetAddressHostName() throws Exception {
        SSLSocketFactory factory = newSocketFactory(true);
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();

        try (ServerSocket serverSocket = newLoopbackServerSocket(loopbackAddress)) {
            try (AcceptedConnection acceptedConnection = acceptAsync(serverSocket);
                    SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                            loopbackAddress, serverSocket.getLocalPort());
                    Socket ignored = acceptedConnection.get()) {
                assertThat(Conscrypt.isConscrypt(sslSocket)).isTrue();
                assertThat(Conscrypt.getHostnameOrIP(sslSocket)).isNotBlank();
            }
        }
    }

    private static SSLSocketFactory newSocketFactory(boolean useEngineSocket) throws Exception {
        Provider provider = Conscrypt.newProvider();
        SSLContext context = SSLContext.getInstance("TLS", provider);
        context.init(null, null, null);
        SSLSocketFactory factory = context.getSocketFactory();
        Conscrypt.setUseEngineSocket(factory, useEngineSocket);
        return factory;
    }

    private static ServerSocket newLoopbackServerSocket() throws Exception {
        return newLoopbackServerSocket(InetAddress.getLoopbackAddress());
    }

    private static ServerSocket newLoopbackServerSocket(InetAddress bindAddress) throws Exception {
        ServerSocket serverSocket = new ServerSocket(0, 1, bindAddress);
        serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        return serverSocket;
    }

    private static AcceptedConnection acceptAsync(ServerSocket serverSocket) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Socket> acceptedSocket = executor.submit(serverSocket::accept);
        return new AcceptedConnection(executor, acceptedSocket);
    }

    private static final class AcceptedConnection implements AutoCloseable {
        private final ExecutorService executor;
        private final Future<Socket> acceptedSocket;

        private AcceptedConnection(ExecutorService executor, Future<Socket> acceptedSocket) {
            this.executor = executor;
            this.acceptedSocket = acceptedSocket;
        }

        private Socket get() throws Exception {
            return acceptedSocket.get(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() throws Exception {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    .isTrue();
        }
    }
}
