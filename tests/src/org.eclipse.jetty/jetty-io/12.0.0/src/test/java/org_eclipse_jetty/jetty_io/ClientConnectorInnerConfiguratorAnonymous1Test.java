/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Promise.Completable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class ClientConnectorInnerConfiguratorAnonymous1Test {
    @Test
    @Timeout(60)
    void connectsToConfiguredUnixDomainPathAndReadsServerData(@TempDir Path directory) throws Exception {
        Path socketPath = directory.resolve("connector.sock");
        UnixDomainSocketAddress serverAddress = UnixDomainSocketAddress.of(socketPath);
        ClientConnector connector = ClientConnector.forUnixDomain(socketPath);
        connector.setConnectTimeout(Duration.ofSeconds(10));
        connector.setIdleTimeout(Duration.ofSeconds(30));
        CompletableFuture<String> received = new CompletableFuture<>();
        Completable<Connection> connected = new Completable<>();
        String message = "hello over unix";

        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                Selector selector = Selector.open()) {
            server.bind(serverAddress);
            server.configureBlocking(false);
            SelectionKey serverKey = server.register(selector, SelectionKey.OP_ACCEPT);
            try {
                connector.start();
                ClientConnectionFactory factory = (endPoint, context) ->
                        new ReadingConnection(endPoint, connector.getExecutor(), received);
                Map<String, Object> context = new HashMap<>();
                context.put(ClientConnector.CLIENT_CONNECTION_FACTORY_CONTEXT_KEY, factory);
                context.put(ClientConnector.CONNECTION_PROMISE_CONTEXT_KEY, connected);
                connector.connect(new InetSocketAddress("localhost", 8080), context);

                assertTrue(selector.select(TimeUnit.SECONDS.toMillis(10)) > 0);
                selector.selectedKeys().clear();
                serverKey.cancel();
                try (SocketChannel accepted = server.accept()) {
                    assertNotNull(accepted);
                    Connection connection = connected.get(10, TimeUnit.SECONDS);
                    assertTrue(connection.getEndPoint().isOpen());
                    assertEquals(serverAddress, connection.getEndPoint().getRemoteSocketAddress());

                    accepted.configureBlocking(false);
                    accepted.register(selector, SelectionKey.OP_WRITE);
                    ByteBuffer output = StandardCharsets.UTF_8.encode(message);
                    while (output.hasRemaining()) {
                        assertTrue(selector.select(TimeUnit.SECONDS.toMillis(10)) > 0);
                        selector.selectedKeys().clear();
                        accepted.write(output);
                    }
                    accepted.shutdownOutput();
                    assertEquals(message, received.get(10, TimeUnit.SECONDS));
                }
            } finally {
                connector.stop();
            }
        }
    }

    private static final class ReadingConnection extends AbstractConnection {
        private final CompletableFuture<String> received;
        private final ByteBuffer input = BufferUtil.allocate(64);

        private ReadingConnection(EndPoint endPoint, Executor executor, CompletableFuture<String> received) {
            super(endPoint, executor);
            this.received = received;
        }

        @Override
        public void onOpen() {
            super.onOpen();
            fillInterested();
        }

        @Override
        public void onFillable() {
            try {
                int filled;
                do {
                    filled = getEndPoint().fill(input);
                } while (filled > 0);
                if (filled < 0) {
                    received.complete(BufferUtil.toString(input, StandardCharsets.UTF_8));
                } else {
                    fillInterested();
                }
            } catch (IOException failure) {
                received.completeExceptionally(failure);
                getEndPoint().close(failure);
            }
        }
    }
}
