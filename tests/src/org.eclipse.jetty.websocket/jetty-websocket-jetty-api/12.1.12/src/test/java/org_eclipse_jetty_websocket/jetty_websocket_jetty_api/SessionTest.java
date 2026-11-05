/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_jetty_api;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Frame;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class SessionTest {
    private Server server;
    private WebSocketClient client;

    @Test
    void negotiatesASessionExchangesMessagesAndClosesNormally() throws Exception {
        EchoEndpoint echo = new EchoEndpoint();
        server = new Server();
        server.setStopTimeout(20000);
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);
        server.setHandler(WebSocketUpgradeHandler.from(server, container -> {
            container.setIdleTimeout(Duration.ofSeconds(30));
            container.addMapping("/chat", (request, response, callback) -> {
                response.setAcceptedSubProtocol("chat");
                response.getHeaders().put("X-Chat", "ready");
                return echo;
            });
        }));
        server.start();

        client = new WebSocketClient();
        client.setConnectTimeout(20000);
        client.setIdleTimeout(Duration.ofSeconds(30));
        client.setStopTimeout(20000);
        client.start();
        URI uri = URI.create("ws://127.0.0.1:" + connector.getLocalPort() + "/chat?room=green");
        ClientUpgradeRequest request = new ClientUpgradeRequest(uri);
        request.setSubProtocols("chat");
        request.setHeader("X-Room", "green");
        request.setTimeout(20, TimeUnit.SECONDS);
        ReceivingEndpoint receiver = new ReceivingEndpoint();

        try (Session session = client.connect(receiver, request).get(20, TimeUnit.SECONDS)) {
            assertSame(session, receiver.getSession());
            assertTrue(receiver.isOpen());
            assertFalse(session.isSecure());
            assertNotNull(session.getLocalSocketAddress());
            assertNotNull(session.getRemoteSocketAddress());
            UpgradeRequest upgradeRequest = session.getUpgradeRequest();
            assertEquals(uri, upgradeRequest.getRequestURI());
            assertEquals("GET", upgradeRequest.getMethod());
            assertEquals("green", upgradeRequest.getHeader("X-Room"));
            assertEquals(List.of("chat"), upgradeRequest.getSubProtocols());
            UpgradeResponse response = session.getUpgradeResponse();
            assertEquals(101, response.getStatusCode());
            assertEquals("chat", response.getAcceptedSubProtocol());
            assertEquals("ready", response.getHeader("X-Chat"));

            session.setIdleTimeout(Duration.ofSeconds(30));
            session.setMaxTextMessageSize(4096);
            session.setMaxBinaryMessageSize(4096);
            assertEquals(Duration.ofSeconds(30), session.getIdleTimeout());
            assertEquals(4096, session.getMaxTextMessageSize());
            assertEquals(4096, session.getMaxBinaryMessageSize());

            Callback.Completable.with(callback -> session.sendText("hello", callback))
                    .get(20, TimeUnit.SECONDS);
            assertEquals("echo:hello", receiver.text.poll(20, TimeUnit.SECONDS));
            Callback.Completable.with(callback -> session.sendPartialText("part-", false, callback))
                    .compose(callback -> session.sendPartialText("two", true, callback))
                    .get(20, TimeUnit.SECONDS);
            assertEquals("echo:part-two", receiver.text.poll(20, TimeUnit.SECONDS));

            byte[] payload = new byte[]{0, 17, -1, -128};
            Callback.Completable.with(callback -> session.sendBinary(ByteBuffer.wrap(payload), callback))
                    .get(20, TimeUnit.SECONDS);
            assertArrayEquals(payload, receiver.binary.poll(20, TimeUnit.SECONDS));
            Callback.Completable.with(callback -> session.sendPing(ByteBuffer.wrap(payload), callback))
                    .get(20, TimeUnit.SECONDS);
            assertArrayEquals(payload, receiver.pong.poll(20, TimeUnit.SECONDS));

            Callback.Completable.with(callback -> session.close(StatusCode.NORMAL, "finished", callback))
                    .get(20, TimeUnit.SECONDS);
            assertEquals(new Frame.CloseStatus(StatusCode.NORMAL, "finished"),
                    receiver.closed.get(20, TimeUnit.SECONDS));
            assertFalse(receiver.isOpen());
        }
    }

    @AfterEach
    void stopResources() throws Exception {
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

    public static class EchoEndpoint extends Session.Listener.AbstractAutoDemanding {
        @Override
        public void onWebSocketText(String message) {
            getSession().sendText("echo:" + message, Callback.NOOP);
        }

        @Override
        public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
            getSession().sendBinary(payload, callback);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason, Callback callback) {
            callback.succeed();
        }
    }

    public static class ReceivingEndpoint extends Session.Listener.AbstractAutoDemanding {
        private final BlockingQueue<String> text = new LinkedBlockingQueue<>();
        private final BlockingQueue<byte[]> binary = new LinkedBlockingQueue<>();
        private final BlockingQueue<byte[]> pong = new LinkedBlockingQueue<>();
        private final CompletableFuture<Frame.CloseStatus> closed = new CompletableFuture<>();

        @Override
        public void onWebSocketText(String message) {
            text.add(message);
        }

        @Override
        public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            binary.add(bytes);
            callback.succeed();
        }

        @Override
        public void onWebSocketPong(ByteBuffer payload) {
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            pong.add(bytes);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason, Callback callback) {
            callback.succeed();
            closed.complete(new Frame.CloseStatus(statusCode, reason));
        }
    }
}
