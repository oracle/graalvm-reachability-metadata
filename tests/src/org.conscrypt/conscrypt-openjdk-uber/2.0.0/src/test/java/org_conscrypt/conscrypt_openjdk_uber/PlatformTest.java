/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformTest {

    @Test
    void wrapsConnectedPlainSocketsUsingTheFileDescriptorSocketPath() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Future<Void> acceptedConnection = acceptUntilClosed(executorService, serverSocket);

            try (Socket plainSocket = new Socket(serverSocket.getInetAddress(), serverSocket.getLocalPort())) {
                assertThat(plainSocket.getChannel()).isNull();

                try (SSLSocket sslSocket = (SSLSocket) createSocketFactory().createSocket(
                        plainSocket,
                        serverSocket.getInetAddress().getHostAddress(),
                        serverSocket.getLocalPort(),
                        true)) {
                    assertThat(Conscrypt.isConscrypt(sslSocket)).isTrue();
                    assertThat(sslSocket.isConnected()).isTrue();
                }
            }

            acceptedConnection.get(30, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void exposesTheWrappedSocketPeerHostNameOrAddress() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        String requestedHostName = "conscrypt-platform.test";

        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Future<Void> acceptedConnection = acceptUntilClosed(executorService, serverSocket);
            InetAddress namedLoopback = InetAddress.getByAddress(requestedHostName, serverSocket.getInetAddress().getAddress());

            try (Socket plainSocket = new Socket()) {
                plainSocket.connect(new InetSocketAddress(namedLoopback, serverSocket.getLocalPort()));
                assertThat(plainSocket.getChannel()).isNull();

                try (SSLSocket sslSocket = (SSLSocket) createSocketFactory().createSocket(
                        plainSocket,
                        null,
                        serverSocket.getLocalPort(),
                        true)) {
                    assertThat(Conscrypt.getHostname(sslSocket)).isNull();
                    assertThat(Conscrypt.getHostnameOrIP(sslSocket)).isIn(requestedHostName, namedLoopback.getHostAddress());
                }
            }

            acceptedConnection.get(30, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static SSLSocketFactory createSocketFactory() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider());
        sslContext.init(null, null, new SecureRandom());

        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        Conscrypt.setUseEngineSocket(socketFactory, false);
        return socketFactory;
    }

    private static Future<Void> acceptUntilClosed(ExecutorService executorService, ServerSocket serverSocket) {
        return executorService.submit(() -> {
            try (Socket acceptedSocket = serverSocket.accept()) {
                acceptedSocket.getInputStream().transferTo(OutputStream.nullOutputStream());
                return null;
            }
        });
    }
}
