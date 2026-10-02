/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class WsWebSocketContainerTest {
    private static final AtomicInteger CONFIGURATOR_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger ENDPOINT_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS = new AtomicInteger();

    @Test
    void annotatedEndpointConnectionInstantiatesCustomConfigurator() {
        CONFIGURATOR_CONSTRUCTIONS.set(0);
        WsWebSocketContainer container = new WsWebSocketContainer();

        assertThatThrownBy(() -> container.connectToServer(AnnotatedClientEndpoint.class,
                URI.create("http://example.invalid/socket"))).isInstanceOf(DeploymentException.class);

        assertThat(CONFIGURATOR_CONSTRUCTIONS).hasValue(1);
    }

    @Test
    void annotatedEndpointClassConnectionInvokesPublicConstructor() throws Exception {
        ENDPOINT_CONSTRUCTIONS.set(0);
        WebSocketContainer container = new WsWebSocketContainer();

        try (TestWebSocketServer server = new TestWebSocketServer();
                Session session = container.connectToServer(ConstructingClientEndpoint.class, server.getUri())) {
            assertThat(session.isOpen()).isTrue();
            assertThat(ENDPOINT_CONSTRUCTIONS).hasValue(1);
        }
    }

    @Test
    void endpointClassConnectionInvokesPublicConstructor() throws Exception {
        PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS.set(0);
        WebSocketContainer container = new WsWebSocketContainer();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

        try (TestWebSocketServer server = new TestWebSocketServer();
                Session session = container.connectToServer(ConstructingEndpoint.class, config, server.getUri())) {
            assertThat(session.isOpen()).isTrue();
            assertThat(PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS).hasValue(1);
        }
    }

    @ClientEndpoint(configurator = CountingConfigurator.class)
    public static class AnnotatedClientEndpoint {
    }

    @ClientEndpoint
    public static class ConstructingClientEndpoint {
        public ConstructingClientEndpoint() {
            ENDPOINT_CONSTRUCTIONS.incrementAndGet();
        }
    }

    public static class ConstructingEndpoint extends Endpoint {
        public ConstructingEndpoint() {
            PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    public static class CountingConfigurator extends ClientEndpointConfig.Configurator {
        public CountingConfigurator() {
            CONFIGURATOR_CONSTRUCTIONS.incrementAndGet();
        }
    }

    private static final class TestWebSocketServer implements AutoCloseable {
        private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        private final ServerSocket serverSocket;
        private final URI uri;
        private final CountDownLatch closeSignal = new CountDownLatch(1);
        private final Thread serverThread;
        private volatile Exception failure;

        private TestWebSocketServer() throws Exception {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(loopback, 0));
            serverSocket.setSoTimeout(10_000);
            uri = new URI("ws", null, loopback.getHostAddress(), serverSocket.getLocalPort(), "/socket", null, null);
            serverThread = new Thread(this::serve, "test-websocket-server");
            serverThread.start();
        }

        private URI getUri() {
            return uri;
        }

        private void serve() {
            try (Socket socket = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                socket.setSoTimeout(10_000);
                String webSocketKey = readWebSocketKey(reader);
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((webSocketKey + WEBSOCKET_GUID).getBytes(StandardCharsets.US_ASCII)));
                OutputStream output = socket.getOutputStream();
                output.write(("HTTP/1.1 101 Switching Protocols\r\n" + "Upgrade: websocket\r\n"
                                + "Connection: Upgrade\r\n" + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                output.flush();
                closeSignal.await(30, TimeUnit.SECONDS);
            } catch (Exception exception) {
                failure = exception;
            }
        }

        private static String readWebSocketKey(BufferedReader reader) throws IOException {
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator > 0 && line.substring(0, separator).equalsIgnoreCase("Sec-WebSocket-Key")) {
                    return line.substring(separator + 1).trim();
                }
            }
            throw new IOException("WebSocket handshake did not contain Sec-WebSocket-Key");
        }

        @Override
        public void close() throws Exception {
            closeSignal.countDown();
            serverSocket.close();
            serverThread.join(10_000);
            if (serverThread.isAlive()) {
                throw new AssertionError("WebSocket test server did not stop");
            }
            if (failure != null) {
                throw new AssertionError("WebSocket test server failed", failure);
            }
        }
    }
}
