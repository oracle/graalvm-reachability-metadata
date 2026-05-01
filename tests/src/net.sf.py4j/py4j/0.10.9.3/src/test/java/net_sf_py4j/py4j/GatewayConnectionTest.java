/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;

import org.junit.jupiter.api.Test;

import py4j.Gateway;
import py4j.GatewayConnection;
import py4j.commands.CallCommand;
import py4j.commands.ConstructorCommand;
import py4j.commands.ReflectionCommand;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayConnectionTest {
    @Test
    void initializesDefaultCommandsWhenConnectionIsCreated() throws IOException {
        try (SocketPair sockets = SocketPair.open()) {
            Gateway gateway = new Gateway(new GatewayEntryPoint());
            TestGatewayConnection connection = new TestGatewayConnection(gateway, sockets.serverSide());

            try {
                assertThat(connection.getSocket()).isSameAs(sockets.serverSide());
                assertThat(connection.commandNames())
                        .contains(CallCommand.CALL_COMMAND_NAME,
                                ConstructorCommand.CONSTRUCTOR_COMMAND_NAME,
                                ReflectionCommand.REFLECTION_COMMAND_NAME);
            } finally {
                connection.shutdown();
            }
        }
    }

    public static class GatewayEntryPoint {
        public String ping() {
            return "pong";
        }
    }

    private static final class TestGatewayConnection extends GatewayConnection {
        private TestGatewayConnection(Gateway gateway, Socket socket) throws IOException {
            super(gateway, socket);
        }

        private Set<String> commandNames() {
            return commands.keySet();
        }
    }

    private static final class SocketPair implements AutoCloseable {
        private final Socket peerSide;
        private final Socket serverSide;

        private SocketPair(Socket peerSide, Socket serverSide) {
            this.peerSide = peerSide;
            this.serverSide = serverSide;
        }

        static SocketPair open() throws IOException {
            try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                Socket peerSide = new Socket(listener.getInetAddress(), listener.getLocalPort());
                Socket serverSide = listener.accept();
                return new SocketPair(peerSide, serverSide);
            }
        }

        Socket serverSide() {
            return serverSide;
        }

        @Override
        public void close() throws IOException {
            IOException exception = null;
            try {
                peerSide.close();
            } catch (IOException e) {
                exception = e;
            }
            try {
                serverSide.close();
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }
}
