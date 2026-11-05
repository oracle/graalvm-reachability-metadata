/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_jetty_common;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class JettyWebSocketFrameHandlerTest {
    private Server server;
    private WebSocketClient client;

    @Test
    void deliversCompleteBinaryMessagesAndAssemblesFragments() throws Exception {
        BinaryReceiver receiver = new BinaryReceiver();
        try (Session session = connect(receiver)) {
            sendFragment(session, new byte[]{0, 1}, false);
            sendFragment(session, new byte[]{127, -1}, true);
            assertArrayEquals(new byte[]{0, 1, 127, -1}, receiver.messages.poll(30, TimeUnit.SECONDS));

            CompletableFuture<Void> sent = new CompletableFuture<>();
            session.sendBinary(ByteBuffer.wrap(new byte[]{42, -128}),
                    Callback.from(() -> sent.complete(null), sent::completeExceptionally));
            sent.get(30, TimeUnit.SECONDS);
            assertArrayEquals(new byte[]{42, -128}, receiver.messages.poll(30, TimeUnit.SECONDS));
            assertTrue(session.isOpen());
        }
    }

    @Test
    void deliversPartialBinaryPayloadsWithTheirFinalFlags() throws Exception {
        PartialBinaryReceiver receiver = new PartialBinaryReceiver();
        try (Session session = connect(receiver)) {
            sendFragment(session, new byte[]{10, 20}, false);
            Fragment first = receiver.fragments.poll(30, TimeUnit.SECONDS);
            assertNotNull(first);
            assertArrayEquals(new byte[]{10, 20}, first.payload());
            assertFalse(first.last());

            sendFragment(session, new byte[]{30, 40}, true);
            Fragment last = receiver.fragments.poll(30, TimeUnit.SECONDS);
            assertNotNull(last);
            assertArrayEquals(new byte[]{30, 40}, last.payload());
            assertTrue(last.last());
            assertTrue(session.isOpen());
        }
    }

    private Session connect(Object endpoint) throws Exception {
        server = new Server();
        server.setStopTimeout(30000);
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);
        server.setHandler(WebSocketUpgradeHandler.from(server, container -> {
            container.setIdleTimeout(Duration.ofSeconds(30));
            container.addMapping("/binary", (request, response, callback) -> endpoint);
        }));
        server.start();

        client = new WebSocketClient();
        client.setConnectTimeout(30000);
        client.setIdleTimeout(Duration.ofSeconds(30));
        client.setStopTimeout(30000);
        client.start();
        URI uri = URI.create("ws://127.0.0.1:" + connector.getLocalPort() + "/binary");
        return client.connect(new Sender(), uri).get(30, TimeUnit.SECONDS);
    }

    private static void sendFragment(Session session, byte[] payload, boolean last) throws Exception {
        CompletableFuture<Void> sent = new CompletableFuture<>();
        session.sendPartialBinary(ByteBuffer.wrap(payload), last,
                Callback.from(() -> sent.complete(null), sent::completeExceptionally));
        sent.get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void stopClientAndServer() throws Exception {
        try {
            if (client != null) {
                client.stop();
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    public static class Sender implements Session.Listener.AutoDemanding {
    }

    public static class BinaryReceiver implements Session.Listener.AutoDemanding {
        private final BlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();

        @Override
        public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            callback.succeed();
            messages.add(bytes);
        }
    }

    public static class PartialBinaryReceiver implements Session.Listener.AutoDemanding {
        private final BlockingQueue<Fragment> fragments = new LinkedBlockingQueue<>();

        @Override
        public void onWebSocketPartialBinary(ByteBuffer payload, boolean last, Callback callback) {
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            callback.succeed();
            fragments.add(new Fragment(bytes, last));
        }
    }

    private record Fragment(byte[] payload, boolean last) {
    }
}
