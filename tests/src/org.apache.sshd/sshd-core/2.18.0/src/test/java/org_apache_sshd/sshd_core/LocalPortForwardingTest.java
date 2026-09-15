/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;

public class LocalPortForwardingTest {
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);
    private static final String PAYLOAD = "forwarded-payload";

    @Test
    void forwardsTcpTrafficThroughAuthenticatedSession() throws Exception {
        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator(AcceptAllPasswordAuthenticator.INSTANCE);
        server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);

        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);

        try (ServerSocket echoServer = new ServerSocket(0);
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            echoServer.setSoTimeout((int) CONNECTION_TIMEOUT.toMillis());

            try {
                server.start();
                client.start();

                try (ClientSession session = client.connect("user", "localhost", server.getPort())
                        .verify(CONNECTION_TIMEOUT)
                        .getSession()) {
                    session.addPasswordIdentity("password");
                    session.auth().verify(CONNECTION_TIMEOUT);

                    Future<String> receivedPayload = executor.submit(() -> echoPayload(echoServer));
                    SshdSocketAddress forwardingAddress = session.startLocalPortForwarding(
                            new SshdSocketAddress("localhost", 0),
                            new SshdSocketAddress("localhost", echoServer.getLocalPort()));
                    try (Socket forwardedSocket = new Socket()) {
                        forwardedSocket.connect(
                                new InetSocketAddress("localhost", forwardingAddress.getPort()),
                                (int) CONNECTION_TIMEOUT.toMillis());
                        forwardedSocket.setSoTimeout((int) CONNECTION_TIMEOUT.toMillis());
                        forwardedSocket.getOutputStream().write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
                        forwardedSocket.getOutputStream().flush();

                        assertThat(new String(
                                forwardedSocket.getInputStream().readNBytes(PAYLOAD.length()),
                                StandardCharsets.UTF_8)).isEqualTo(PAYLOAD);
                        assertThat(receivedPayload.get(CONNECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                                .isEqualTo(PAYLOAD);
                    } finally {
                        session.stopLocalPortForwarding(forwardingAddress);
                    }
                }
            } finally {
                client.stop();
                server.stop(true);
            }
        }
    }

    private static String echoPayload(ServerSocket echoServer) throws IOException {
        try (Socket socket = echoServer.accept()) {
            socket.setSoTimeout((int) CONNECTION_TIMEOUT.toMillis());
            byte[] payload = socket.getInputStream().readNBytes(PAYLOAD.length());
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
            return new String(payload, StandardCharsets.UTF_8);
        }
    }
}
