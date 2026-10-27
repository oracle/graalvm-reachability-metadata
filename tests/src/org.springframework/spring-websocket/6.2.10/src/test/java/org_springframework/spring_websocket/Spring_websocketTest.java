/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.LoggingWebSocketHandlerDecorator;
import org.springframework.web.socket.handler.PerConnectionWebSocketHandler;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.sockjs.frame.DefaultSockJsFrameFormat;
import org.springframework.web.socket.sockjs.frame.SockJsFrame;
import org.springframework.web.socket.sockjs.frame.SockJsFrameType;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_websocketTest {
    @Test
    void websocketValueTypesAndHandshakeHeadersRoundTrip() {
        List<WebSocketExtension> extensions =
                WebSocketExtension.parseExtensions(
                        "permessage-deflate; client_max_window_bits=15, x-trace");
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketKey("client-key");
        headers.setSecWebSocketProtocol(List.of("v12.stomp", "chat"));
        headers.setSecWebSocketVersion("13");
        headers.setSecWebSocketExtensions(extensions);

        assertThat(headers.getSecWebSocketKey()).isEqualTo("client-key");
        assertThat(headers.getSecWebSocketProtocol()).containsExactly("v12.stomp", "chat");
        assertThat(headers.getSecWebSocketVersion()).isEqualTo("13");
        assertThat(headers.getSecWebSocketExtensions()).containsExactlyElementsOf(extensions);
        assertThat(extensions.get(0).getName()).isEqualTo("permessage-deflate");
        assertThat(extensions.get(0).getParameters())
                .containsEntry("client_max_window_bits", "15");

        TextMessage text = new TextMessage("hello", false);
        BinaryMessage binary = new BinaryMessage(new byte[] {1, 2, 3});
        PingMessage ping = new PingMessage(ByteBuffer.wrap(new byte[] {4}));
        PongMessage pong = new PongMessage(ByteBuffer.wrap(new byte[] {5, 6}));
        CloseStatus closeStatus = CloseStatus.NORMAL.withReason("complete");

        assertThat(text.getPayload()).isEqualTo("hello");
        assertThat(text.asBytes()).containsExactly(104, 101, 108, 108, 111);
        assertThat(text.isLast()).isFalse();
        assertThat(binary.getPayloadLength()).isEqualTo(3);
        assertThat(binary.getPayload()).isEqualTo(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        assertThat(ping.getPayloadLength()).isEqualTo(1);
        assertThat(pong.getPayloadLength()).isEqualTo(2);
        assertThat(closeStatus.getCode()).isEqualTo(1000);
        assertThat(closeStatus.getReason()).isEqualTo("complete");
        assertThat(closeStatus.equalsCode(CloseStatus.NORMAL)).isTrue();
    }

    @Test
    void decoratorChainDelegatesTheCompleteTextHandlerLifecycle() throws Exception {
        RecordingTextHandler target = new RecordingTextHandler();
        WebSocketHandlerDecorator inner = new WebSocketHandlerDecorator(target);
        LoggingWebSocketHandlerDecorator outer = new LoggingWebSocketHandlerDecorator(inner);
        TestWebSocketSession session = new TestWebSocketSession("decorated", "chat");
        IOException transportFailure = new IOException("transport");

        outer.afterConnectionEstablished(session);
        outer.handleMessage(session, new TextMessage("payload"));
        outer.handleTransportError(session, transportFailure);
        outer.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(outer.getDelegate()).isSameAs(inner);
        assertThat(outer.getLastHandler()).isSameAs(target);
        assertThat(WebSocketHandlerDecorator.unwrap(outer)).isSameAs(target);
        assertThat(target.events)
                .containsExactly(
                        "opened:decorated",
                        "text:decorated=payload",
                        "error:decorated=transport",
                        "closed:decorated=1000");
    }

    @Test
    void perConnectionHandlerCreatesAndRoutesToIndependentHandlerInstances() throws Exception {
        InstanceTrackingTextHandler.reset();
        PerConnectionWebSocketHandler handler =
                new PerConnectionWebSocketHandler(InstanceTrackingTextHandler.class, true);
        TestWebSocketSession first = new TestWebSocketSession("first", "chat");
        TestWebSocketSession second = new TestWebSocketSession("second", "chat");

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.handleMessage(first, new TextMessage("one"));
        handler.handleMessage(second, new TextMessage("two"));
        handler.handleTransportError(first, new IOException("transport"));
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        handler.afterConnectionClosed(second, CloseStatus.GOING_AWAY);

        assertThat(handler.supportsPartialMessages()).isTrue();
        assertThat(InstanceTrackingTextHandler.EVENTS)
                .containsExactly(
                        "opened:1:first",
                        "opened:2:second",
                        "text:1:first=one",
                        "text:2:second=two",
                        "error:1:first=transport",
                        "closed:1:first=1000",
                        "closed:2:second=1001");
    }

    @Test
    void concurrentSessionDecoratorDropsOldestBufferedMessageOnOverflow() throws Exception {
        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSend = new CountDownLatch(1);
        TestWebSocketSession delegate = new TestWebSocketSession("concurrent", "chat");
        BlockingWebSocketSession blockingSession =
                new BlockingWebSocketSession(delegate, firstSendStarted, releaseFirstSend);
        ConcurrentWebSocketSessionDecorator session =
                new ConcurrentWebSocketSessionDecorator(
                        blockingSession,
                        30_000,
                        6,
                        ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
        List<WebSocketMessage<?>> bufferedMessages = new CopyOnWriteArrayList<>();
        session.setMessageCallback(bufferedMessages::add);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TextMessage first = new TextMessage("first");
        TextMessage second = new TextMessage("second");
        TextMessage third = new TextMessage("third");

        Future<Void> firstSend =
                executor.submit(
                        () -> {
                            session.sendMessage(first);
                            return null;
                        });
        try {
            assertThat(firstSendStarted.await(10, TimeUnit.SECONDS)).isTrue();

            session.sendMessage(second);
            assertThat(session.getBufferSize()).isEqualTo(second.getPayloadLength());

            session.sendMessage(third);

            assertThat(session.getSendTimeLimit()).isEqualTo(30_000);
            assertThat(session.getBufferSizeLimit()).isEqualTo(6);
            assertThat(session.getOverflowStrategy())
                    .isEqualTo(ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
            assertThat(session.getBufferSize()).isEqualTo(third.getPayloadLength());
            assertThat(bufferedMessages).containsExactly(first, second, third);

            releaseFirstSend.countDown();
            firstSend.get(10, TimeUnit.SECONDS);

            assertThat(delegate.sentMessages).containsExactly(first, third);
            assertThat(session.getBufferSize()).isZero();
        } finally {
            releaseFirstSend.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void stompSubProtocolRoutesFramesBetweenWebSocketAndMessageChannels() throws Exception {
        ExecutorSubscribableChannel inboundChannel = new ExecutorSubscribableChannel();
        ExecutorSubscribableChannel outboundChannel = new ExecutorSubscribableChannel();
        RecordingMessageHandler inboundRecorder = new RecordingMessageHandler();
        inboundChannel.subscribe(inboundRecorder);

        StompSubProtocolHandler stompHandler = new StompSubProtocolHandler();
        SubProtocolWebSocketHandler webSocketHandler =
                new SubProtocolWebSocketHandler(inboundChannel, outboundChannel);
        webSocketHandler.addProtocolHandler(stompHandler);
        webSocketHandler.start();

        TestWebSocketSession session = new TestWebSocketSession("stomp-session", "v12.stomp");
        try {
            webSocketHandler.afterConnectionEstablished(session);
            webSocketHandler.handleMessage(
                    session,
                    new TextMessage("CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0"));

            assertThat(inboundRecorder.messages).hasSize(1);
            Message<?> connectMessage = inboundRecorder.messages.get(0);
            assertThat(StompHeaderAccessor.getCommand(connectMessage.getHeaders()))
                    .isEqualTo(StompCommand.CONNECT);
            assertThat(StompHeaderAccessor.wrap(connectMessage).getSessionId())
                    .isEqualTo("stomp-session");
            assertThat(stompHandler.getStats().getTotalConnect()).isEqualTo(1);

            StompHeaderAccessor connectedHeaders =
                    StompHeaderAccessor.create(StompCommand.CONNECTED);
            connectedHeaders.setSessionId("stomp-session");
            connectedHeaders.setVersion("1.2");
            Message<byte[]> connectedMessage =
                    MessageBuilder.createMessage(
                            new byte[0], connectedHeaders.getMessageHeaders());

            assertThat(outboundChannel.send(connectedMessage)).isTrue();
            assertThat(session.sentMessages).hasSize(1);
            assertThat(session.sentMessages.get(0)).isInstanceOf(TextMessage.class);
            String outboundFrame = ((TextMessage) session.sentMessages.get(0)).getPayload();
            assertThat(outboundFrame)
                    .startsWith("CONNECTED\n")
                    .contains("version:1.2")
                    .endsWith("\0");
            assertThat(stompHandler.getStats().getTotalConnected()).isEqualTo(1);

            webSocketHandler.handleMessage(
                    session, new TextMessage("DISCONNECT\nreceipt:client-receipt\n\n\0"));
            assertThat(inboundRecorder.messages).hasSize(2);
            assertThat(
                            StompHeaderAccessor.getCommand(
                                    inboundRecorder.messages.get(1).getHeaders()))
                    .isEqualTo(StompCommand.DISCONNECT);
            assertThat(stompHandler.getStats().getTotalDisconnect()).isEqualTo(1);

            webSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);
            assertThat(inboundRecorder.messages).hasSize(3);
            Message<?> sessionEndedMessage = inboundRecorder.messages.get(2);
            assertThat(StompHeaderAccessor.getCommand(sessionEndedMessage.getHeaders()))
                    .isEqualTo(StompCommand.DISCONNECT);
            assertThat(StompHeaderAccessor.wrap(sessionEndedMessage).getSessionId())
                    .isEqualTo("stomp-session");
            assertThat(webSocketHandler.getStats().getTotalSessions()).isEqualTo(1);
        } finally {
            webSocketHandler.stop();
        }
    }

    @Test
    void simpUserRegistryTracksSessionAndSubscriptionEvents() {
        DefaultSimpUserRegistry registry = new DefaultSimpUserRegistry();
        Principal principal = new NamedPrincipal("alice");
        String sessionId = "registry-session";

        StompHeaderAccessor connectedHeaders =
                StompHeaderAccessor.create(StompCommand.CONNECTED);
        connectedHeaders.setSessionId(sessionId);
        Message<byte[]> connectedMessage =
                MessageBuilder.createMessage(new byte[0], connectedHeaders.getMessageHeaders());
        registry.onApplicationEvent(
                new SessionConnectedEvent(this, connectedMessage, principal));

        StompHeaderAccessor subscribeHeaders =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeHeaders.setSessionId(sessionId);
        subscribeHeaders.setSubscriptionId("subscription-1");
        subscribeHeaders.setDestination("/topic/updates");
        Message<byte[]> subscribeMessage =
                MessageBuilder.createMessage(new byte[0], subscribeHeaders.getMessageHeaders());
        registry.onApplicationEvent(
                new SessionSubscribeEvent(this, subscribeMessage, principal));

        SimpUser user = registry.getUser("alice");
        assertThat(user).isNotNull();
        assertThat(registry.getUserCount()).isEqualTo(1);
        assertThat(user.getPrincipal()).isSameAs(principal);
        SimpSession simpSession = user.getSession(sessionId);
        assertThat(simpSession).isNotNull();
        assertThat(simpSession.getSubscriptions()).hasSize(1);
        SimpSubscription subscription = simpSession.getSubscriptions().iterator().next();
        assertThat(subscription.getId()).isEqualTo("subscription-1");
        assertThat(subscription.getDestination()).isEqualTo("/topic/updates");
        assertThat(
                        registry.findSubscriptions(
                                candidate -> candidate.getDestination().startsWith("/topic/")))
                .containsExactly(subscription);

        StompHeaderAccessor unsubscribeHeaders =
                StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        unsubscribeHeaders.setSessionId(sessionId);
        unsubscribeHeaders.setSubscriptionId("subscription-1");
        Message<byte[]> unsubscribeMessage =
                MessageBuilder.createMessage(new byte[0], unsubscribeHeaders.getMessageHeaders());
        registry.onApplicationEvent(
                new SessionUnsubscribeEvent(this, unsubscribeMessage, principal));
        assertThat(simpSession.getSubscriptions()).isEmpty();

        StompHeaderAccessor disconnectHeaders =
                StompHeaderAccessor.create(StompCommand.DISCONNECT);
        disconnectHeaders.setSessionId(sessionId);
        Message<byte[]> disconnectMessage =
                MessageBuilder.createMessage(new byte[0], disconnectHeaders.getMessageHeaders());
        registry.onApplicationEvent(
                new SessionDisconnectEvent(
                        this,
                        disconnectMessage,
                        sessionId,
                        CloseStatus.NORMAL,
                        principal));

        assertThat(registry.getUser("alice")).isNull();
        assertThat(registry.getUsers()).isEmpty();
    }

    @Test
    void sockJsFramesExposeProtocolTypesDataAndFormatting() {
        SockJsFrame open = SockJsFrame.openFrame();
        SockJsFrame heartbeat = SockJsFrame.heartbeatFrame();
        SockJsFrame message = new SockJsFrame("a[\"first\",\"second\"]");
        SockJsFrame close = SockJsFrame.closeFrame(3001, "finished");
        DefaultSockJsFrameFormat eventStreamFormat =
                new DefaultSockJsFrameFormat("data: %s\r\n\r\n");

        assertThat(open.getType()).isEqualTo(SockJsFrameType.OPEN);
        assertThat(open.getContent()).isEqualTo("o");
        assertThat(open.getFrameData()).isNull();
        assertThat(heartbeat.getType()).isEqualTo(SockJsFrameType.HEARTBEAT);
        assertThat(message.getType()).isEqualTo(SockJsFrameType.MESSAGE);
        assertThat(message.getFrameData()).isEqualTo("[\"first\",\"second\"]");
        assertThat(close.getType()).isEqualTo(SockJsFrameType.CLOSE);
        assertThat(close.getFrameData()).isEqualTo("[3001,\"finished\"]");
        assertThat(eventStreamFormat.format(message))
                .isEqualTo("data: a[\"first\",\"second\"]\r\n\r\n");
        assertThat(close.getContentBytes())
                .containsExactly(close.getContent().getBytes(SockJsFrame.CHARSET));
    }

    public static final class InstanceTrackingTextHandler extends TextWebSocketHandler {
        private static final AtomicInteger NEXT_ID = new AtomicInteger();
        private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

        private final int instanceId;

        public InstanceTrackingTextHandler() {
            this.instanceId = NEXT_ID.incrementAndGet();
        }

        private static void reset() {
            NEXT_ID.set(0);
            EVENTS.clear();
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            EVENTS.add("opened:" + this.instanceId + ":" + session.getId());
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            EVENTS.add(
                    "text:"
                            + this.instanceId
                            + ":"
                            + session.getId()
                            + "="
                            + message.getPayload());
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            EVENTS.add(
                    "error:"
                            + this.instanceId
                            + ":"
                            + session.getId()
                            + "="
                            + exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            EVENTS.add(
                    "closed:"
                            + this.instanceId
                            + ":"
                            + session.getId()
                            + "="
                            + status.getCode());
        }
    }

    private static final class BlockingWebSocketSession extends WebSocketSessionDecorator {
        private final CountDownLatch firstSendStarted;
        private final CountDownLatch releaseFirstSend;
        private final AtomicInteger sendCount = new AtomicInteger();

        private BlockingWebSocketSession(
                WebSocketSession delegate,
                CountDownLatch firstSendStarted,
                CountDownLatch releaseFirstSend) {
            super(delegate);
            this.firstSendStarted = firstSendStarted;
            this.releaseFirstSend = releaseFirstSend;
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException {
            if (this.sendCount.incrementAndGet() == 1) {
                this.firstSendStarted.countDown();
                try {
                    if (!this.releaseFirstSend.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release the first send");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException(
                            "Interrupted while waiting to release the first send", exception);
                }
            }
            super.sendMessage(message);
        }
    }

    private static final class RecordingTextHandler extends TextWebSocketHandler {
        private final List<String> events = new ArrayList<>();

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            this.events.add("opened:" + session.getId());
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            this.events.add("text:" + session.getId() + "=" + message.getPayload());
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            this.events.add("error:" + session.getId() + "=" + exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            this.events.add("closed:" + session.getId() + "=" + status.getCode());
        }
    }

    private static final class RecordingMessageHandler implements MessageHandler {
        private final List<Message<?>> messages = new ArrayList<>();

        @Override
        public void handleMessage(Message<?> message) {
            this.messages.add(message);
        }
    }

    private static final class NamedPrincipal implements Principal {
        private final String name;

        private NamedPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    private static final class TestWebSocketSession implements WebSocketSession {
        private final String id;
        private final String acceptedProtocol;
        private final URI uri;
        private final HttpHeaders handshakeHeaders = new HttpHeaders();
        private final Map<String, Object> attributes = new HashMap<>();
        private final Principal principal;
        private final List<WebSocketMessage<?>> sentMessages = new ArrayList<>();

        private int textMessageSizeLimit = 64 * 1024;
        private int binaryMessageSizeLimit = 64 * 1024;
        private boolean open = true;

        private TestWebSocketSession(String id, String acceptedProtocol) {
            this.id = id;
            this.acceptedProtocol = acceptedProtocol;
            this.uri = URI.create("ws://localhost/socket/" + id);
            this.principal = new NamedPrincipal("user-" + id);
        }

        @Override
        public String getId() {
            return this.id;
        }

        @Override
        public URI getUri() {
            return this.uri;
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return this.handshakeHeaders;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return this.attributes;
        }

        @Override
        public Principal getPrincipal() {
            return this.principal;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 55000);
        }

        @Override
        public String getAcceptedProtocol() {
            return this.acceptedProtocol;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
            this.textMessageSizeLimit = messageSizeLimit;
        }

        @Override
        public int getTextMessageSizeLimit() {
            return this.textMessageSizeLimit;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
            this.binaryMessageSizeLimit = messageSizeLimit;
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return this.binaryMessageSizeLimit;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return List.of();
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException {
            if (!this.open) {
                throw new IOException("Session is closed");
            }
            this.sentMessages.add(message);
        }

        @Override
        public boolean isOpen() {
            return this.open;
        }

        @Override
        public void close() {
            close(CloseStatus.NORMAL);
        }

        @Override
        public void close(CloseStatus status) {
            this.open = false;
        }
    }
}
