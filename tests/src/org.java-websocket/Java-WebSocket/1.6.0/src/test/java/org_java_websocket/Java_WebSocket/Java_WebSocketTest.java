/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_java_websocket.Java_WebSocket;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.Opcode;
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class Java_WebSocketTest {
    private static final long WAIT_SECONDS = 10;
    private static final int NETWORK_TIMEOUT_MILLIS = 10_000;

    @Test
    @Timeout(value = 55)
    void clientAndServerNegotiateAndExchangeWebSocketFrames() throws Exception {
        RecordingServer server = new RecordingServer();
        RecordingClient client = null;
        try {
            server.start();
            assertThat(server.started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.error.get()).isNull();

            URI endpoint = URI.create("ws://127.0.0.1:" + server.getPort() + "/messages?tenant=acme");
            client = new RecordingClient(endpoint);
            assertThat(client.connectBlocking(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.opened.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            WebSocket serverConnection = server.connection.get();
            assertThat(serverConnection).isNotNull();
            assertThat(server.resourceDescriptor.get()).isEqualTo("/messages?tenant=acme");
            assertThat(server.requestHeader.get()).isEqualTo("integration-test");
            assertThat(serverConnection.<String>getAttachment()).isEqualTo("session-1");
            assertThat(serverConnection.getProtocol().getProvidedProtocol()).isEqualTo("chat");
            assertThat(serverConnection.hasSSLSupport()).isFalse();
            assertThat(serverConnection.getLocalSocketAddress()).isNotNull();
            assertThat(serverConnection.getRemoteSocketAddress()).isNotNull();
            assertThat(client.openHandshake.get()).isNotNull();
            assertThat(client.openHandshake.get().getHttpStatus()).isEqualTo((short) 101);
            assertThat(client.getConnection().getProtocol().getProvidedProtocol()).isEqualTo("chat");

            String compressedMessage = "compressible-payload-".repeat(200);
            client.send(compressedMessage);
            assertThat(client.textMessages.poll(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("echo:" + compressedMessage);

            byte[] binaryPayload = "binary-payload".getBytes(StandardCharsets.UTF_8);
            client.send(binaryPayload);
            assertThat(client.binaryMessages.poll(WAIT_SECONDS, TimeUnit.SECONDS)).containsExactly(binaryPayload);

            client.sendFragmentedFrame(Opcode.TEXT, StandardCharsets.UTF_8.encode("fragmented-"), false);
            client.sendFragmentedFrame(Opcode.TEXT, StandardCharsets.UTF_8.encode("message"), true);
            assertThat(client.textMessages.poll(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("echo:fragmented-message");

            server.broadcast("server-broadcast");
            assertThat(client.textMessages.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("server-broadcast");

            client.sendPing();
            assertThat(client.pongReceived.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            client.close(CloseFrame.NORMAL, "complete");
            assertThat(client.closed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.closed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(client.closeCode).isEqualTo(CloseFrame.NORMAL);
            assertThat(client.closeReason).isEqualTo("complete");
            assertThat(server.closeCode).isEqualTo(CloseFrame.NORMAL);
            assertThat(server.closeReason).isEqualTo("complete");
            assertThat(client.error.get()).isNull();
            assertThat(server.error.get()).isNull();
        } finally {
            if (client != null && !client.isClosed()) {
                client.close();
                client.closed.await(WAIT_SECONDS, TimeUnit.SECONDS);
            }
            server.stop(NETWORK_TIMEOUT_MILLIS);
        }
    }

    private static Draft compressedChatDraft() {
        PerMessageDeflateExtension extension = new PerMessageDeflateExtension();
        extension.setThreshold(64);
        return new Draft_6455(List.of(extension), List.of(new Protocol("chat")));
    }

    private static byte[] copyRemaining(ByteBuffer source) {
        ByteBuffer copy = source.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private static final class RecordingServer extends WebSocketServer {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch opened = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicReference<WebSocket> connection = new AtomicReference<>();
        private final AtomicReference<String> resourceDescriptor = new AtomicReference<>();
        private final AtomicReference<String> requestHeader = new AtomicReference<>();
        private final AtomicReference<Exception> error = new AtomicReference<>();
        private volatile int closeCode;
        private volatile String closeReason;

        private RecordingServer() {
            super(new InetSocketAddress("127.0.0.1", 0), 1, List.of(compressedChatDraft()));
        }

        @Override
        public void onStart() {
            started.countDown();
        }

        @Override
        public void onOpen(WebSocket socket, ClientHandshake handshake) {
            socket.setAttachment("session-1");
            connection.set(socket);
            resourceDescriptor.set(handshake.getResourceDescriptor());
            requestHeader.set(handshake.getFieldValue("X-Test-Client"));
            opened.countDown();
        }

        @Override
        public void onMessage(WebSocket socket, String message) {
            socket.send("echo:" + message);
        }

        @Override
        public void onMessage(WebSocket socket, ByteBuffer message) {
            socket.send(copyRemaining(message));
        }

        @Override
        public void onClose(WebSocket socket, int code, String reason, boolean remote) {
            closeCode = code;
            closeReason = reason;
            closed.countDown();
        }

        @Override
        public void onError(WebSocket socket, Exception exception) {
            error.compareAndSet(null, exception);
            started.countDown();
        }
    }

    private static final class RecordingClient extends WebSocketClient {
        private final BlockingQueue<String> textMessages = new LinkedBlockingQueue<>();
        private final BlockingQueue<byte[]> binaryMessages = new LinkedBlockingQueue<>();
        private final CountDownLatch pongReceived = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicReference<ServerHandshake> openHandshake = new AtomicReference<>();
        private final AtomicReference<Exception> error = new AtomicReference<>();
        private volatile int closeCode;
        private volatile String closeReason;

        private RecordingClient(URI endpoint) {
            super(endpoint, compressedChatDraft(), Map.of("X-Test-Client", "integration-test"),
                    NETWORK_TIMEOUT_MILLIS);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            openHandshake.set(handshake);
        }

        @Override
        public void onMessage(String message) {
            textMessages.add(message);
        }

        @Override
        public void onMessage(ByteBuffer message) {
            binaryMessages.add(copyRemaining(message));
        }

        @Override
        public void onWebsocketPong(WebSocket socket, Framedata frame) {
            super.onWebsocketPong(socket, frame);
            pongReceived.countDown();
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closeCode = code;
            closeReason = reason;
            closed.countDown();
        }

        @Override
        public void onError(Exception exception) {
            error.compareAndSet(null, exception);
        }
    }
}
