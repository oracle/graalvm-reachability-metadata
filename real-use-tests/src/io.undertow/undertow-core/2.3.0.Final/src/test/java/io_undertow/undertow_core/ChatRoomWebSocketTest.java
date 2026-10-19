/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * WebSocket endpoints with `Handlers.websocket`: the upgrade handshake, buffered text frames
 * read through `AbstractReceiveListener`, broadcasting to peer connections with `WebSockets`,
 * server-initiated close frames, close tasks, and falling through to a plain HTTP handler.
 *
 * A chat room runs on an embedded Undertow server bound to an ephemeral port. Members join with
 * their name in the query string, everyone in the room sees join, chat, and leave notices, and a
 * member who types `/quit` is disconnected by the server with an application close code. A plain
 * HTTP request to the same path, without the upgrade headers, is answered by the room's info
 * page. The clients are the JDK `WebSocket` and `HttpClient`, so every assertion is on what goes
 * over the wire.
 */
public class ChatRoomWebSocketTest {

    private static final long TIMEOUT_SECONDS = 10;

    /// The close code the room sends to a member who leaves with `/quit`.
    private static final int QUIT_CLOSE_CODE = 4000;

    private Undertow server;
    private HttpClient client;
    private URI httpBase;
    private URI wsBase;

    @BeforeEach
    void startServer() {
        ChatRoom room = new ChatRoom();
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(Handlers.path()
                        .addPrefixPath("/chat", Handlers.websocket(room, ChatRoom::info)))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        httpBase = URI.create("http://127.0.0.1:" + address.getPort());
        wsBase = URI.create("ws://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void broadcastsJoinAndChatMessagesToEveryMember() throws Exception {
        Member alice = join("alice");
        assertEquals("* alice joined", alice.next());

        Member bob = join("bob");
        assertEquals("* bob joined", alice.next());
        assertEquals("* bob joined", bob.next());

        bob.say("hello, room");
        assertEquals("bob: hello, room", alice.next());
        assertEquals("bob: hello, room", bob.next());

        alice.say("hi bob");
        assertEquals("alice: hi bob", alice.next());
        assertEquals("alice: hi bob", bob.next());

        alice.leave();
        bob.leave();
    }

    @Test
    void closesTheQuittingMemberAndTellsTheOthers() throws Exception {
        Member alice = join("alice");
        assertEquals("* alice joined", alice.next());
        Member bob = join("bob");
        assertEquals("* bob joined", alice.next());
        assertEquals("* bob joined", bob.next());

        alice.say("/quit");
        assertEquals(QUIT_CLOSE_CODE + " goodbye alice", alice.closed());
        assertEquals("* alice left", bob.next());

        bob.say("anyone here?");
        assertEquals("bob: anyone here?", bob.next());
        bob.leave();
    }

    @Test
    void answersPlainHttpRequestsWithTheNextHandler() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(httpBase.resolve("/chat"))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("text/plain", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("connect with a WebSocket client to /chat?user=<name>", response.body());
    }

    private Member join(String user) throws Exception {
        Member member = new Member();
        member.socket = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .buildAsync(wsBase.resolve("/chat?user=" + user), member)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return member;
    }

    /// The server side: one handshake callback per member, sharing the room through peer sets.
    private static final class ChatRoom implements WebSocketConnectionCallback {

        static void info(HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("connect with a WebSocket client to /chat?user=<name>");
        }

        @Override
        public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
            List<String> users = exchange.getRequestParameters().get("user");
            String user = users == null ? "anonymous" : users.get(0);
            channel.addCloseTask(closed -> broadcast(closed, "* " + user + " left"));
            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel from, BufferedTextMessage message) {
                    String text = message.getData();
                    if (text.equals("/quit")) {
                        WebSockets.sendClose(QUIT_CLOSE_CODE, "goodbye " + user, from, null);
                    } else {
                        broadcast(from, user + ": " + text);
                    }
                }
            });
            channel.resumeReceives();
            broadcast(channel, "* " + user + " joined");
        }

        /// Sends `text` to every open connection of the room `channel` belongs to.
        private static void broadcast(WebSocketChannel channel, String text) {
            for (WebSocketChannel peer : channel.getPeerConnections()) {
                if (peer.isOpen() && !peer.isCloseFrameReceived()) {
                    WebSockets.sendText(text, peer, null);
                }
            }
        }
    }

    /// The client side: a JDK WebSocket listener that queues every text frame and the close frame.
    private static final class Member implements WebSocket.Listener {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<String> close = new CompletableFuture<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            close.complete(statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            close.completeExceptionally(error);
        }

        void say(String text) throws Exception {
            socket.sendText(text, true).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        String next() throws InterruptedException {
            String message = messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(message, "no message within " + TIMEOUT_SECONDS + " seconds");
            return message;
        }

        String closed() throws Exception {
            return close.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        void leave() throws Exception {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            closed();
        }
    }
}
