/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.ChannelForwardedTCPIP;
import com.jcraft.jsch.ForwardedTCPIPDaemon;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ChannelForwardedTCPIPTest {
    /// The port the SSH server listens on inside the container for the remote forwarding.
    private static final int REMOTE_PORT = 10022;

    private static SftpServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = SftpServer.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void instantiatesTheNamedDaemonForAConnectionToTheRemotelyForwardedPort() throws Exception {
        Session session = server.connect();
        try {
            session.setPortForwardingR(REMOTE_PORT, GreetingDaemon.class.getName(), new Object[] {"hello"});
            // Connecting to the remote listener from inside the server makes it open a forwarded channel.
            int localPort = findAvailablePort();
            session.setPortForwardingL(localPort, "127.0.0.1", REMOTE_PORT);
            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress(SftpServer.HOST, localPort), SftpServer.TIMEOUT_MILLIS);
                client.setSoTimeout(SftpServer.TIMEOUT_MILLIS);
                client.getOutputStream().write("jsch\n".getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                assertThat(reader.readLine()).isEqualTo("hello, jsch");
            }
            session.delPortForwardingL(localPort);
            session.delPortForwardingR(REMOTE_PORT);
        } finally {
            session.disconnect();
        }
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /// Answers the first line of each forwarded connection with the greeting passed as its argument.
    public static final class GreetingDaemon implements ForwardedTCPIPDaemon {
        private InputStream input;
        private OutputStream output;
        private String greeting;

        public GreetingDaemon() {
        }

        @Override
        public void setChannel(ChannelForwardedTCPIP channel) {
            // The streams must be attached before the channel starts pumping data.
            try {
                input = channel.getInputStream();
                output = channel.getOutputStream();
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot attach to the forwarded channel", exception);
            }
        }

        @Override
        public void setArg(Object[] arguments) {
            greeting = (String) arguments[0];
        }

        @Override
        public void run() {
            try (OutputStream response = output) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII));
                String request = reader.readLine();
                response.write((greeting + ", " + request + "\n").getBytes(StandardCharsets.US_ASCII));
                response.flush();
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot answer the forwarded connection", exception);
            }
        }
    }
}
