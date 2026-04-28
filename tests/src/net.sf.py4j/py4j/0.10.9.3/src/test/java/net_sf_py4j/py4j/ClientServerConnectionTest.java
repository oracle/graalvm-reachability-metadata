/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;

import org.junit.jupiter.api.Test;

import py4j.ClientServerConnection;
import py4j.Gateway;
import py4j.GatewayServer;
import py4j.JavaServer;
import py4j.PythonClient;

public class ClientServerConnectionTest {
    @Test
    void initializesDefaultCommandHandlersForAcceptedSocket() throws Exception {
        PythonClient pythonClient = new PythonClient(null, null, 0, GatewayServer.defaultAddress(), 0,
                TimeUnit.SECONDS, SocketFactory.getDefault(), null);
        JavaServer javaServer = new JavaServer(new EntryPoint(), 0, 0, 0, null, pythonClient);
        pythonClient.setJavaServer(javaServer);
        Gateway gateway = javaServer.getGateway();

        try (SocketPair sockets = SocketPair.open()) {
            ClientServerConnection connection = new ClientServerConnection(gateway, sockets.serverSocket, null,
                    pythonClient, javaServer, 0);

            assertThat(connection.getSocket()).isSameAs(sockets.serverSocket);
            assertThat(connection.getSocket().isConnected()).isTrue();
            assertThat(connection.getSocket().isClosed()).isFalse();
        }
    }

    public static class EntryPoint {
        public String ping() {
            return "pong";
        }
    }

    private static final class SocketPair implements AutoCloseable {
        private final Socket clientSocket;
        private final Socket serverSocket;

        private SocketPair(Socket clientSocket, Socket serverSocket) {
            this.clientSocket = clientSocket;
            this.serverSocket = serverSocket;
        }

        private static SocketPair open() throws IOException {
            InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
            try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
                Socket clientSocket = new Socket(loopbackAddress, serverSocket.getLocalPort());
                Socket acceptedSocket = serverSocket.accept();
                return new SocketPair(clientSocket, acceptedSocket);
            }
        }

        @Override
        public void close() throws IOException {
            clientSocket.close();
            serverSocket.close();
        }
    }
}
