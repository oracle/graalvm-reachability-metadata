/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.WebSocketClientRegistry;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Micronaut_websocketTest {
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(10);
    private static final long EVENT_TIMEOUT_SECONDS = 10;

    @Test
    @Timeout(55)
    void exchangesTextAndDispatchesLifecycleAndErrorCallbacks() throws Exception {
        TextSocket.clearEvents();

        try (EmbeddedServer server = startServer();
                WebSocketClient webSocketClient = createClient(server);
                TextClient client = connect(webSocketClient, TextClient.class, "/websocket/engineering")) {
            assertThat(client.takeMessage()).isEqualTo("opened:engineering");
            assertThat(TextSocket.takeEvent()).startsWith("open:engineering:");

            client.send("hello");
            assertThat(client.takeMessage()).isEqualTo("engineering:HELLO");
            assertThat(TextSocket.takeEvent()).isEqualTo("message:engineering:hello");

            client.close();
            CloseReason clientCloseReason = client.takeCloseReason();
            assertThat(clientCloseReason).isNotNull();
            assertThat(clientCloseReason.getCode()).isBetween(1000, 4999);
            assertThat(clientCloseReason.getReason()).isNotBlank();
            assertThat(TextSocket.takeEvent()).startsWith("close:engineering:");

            try (TextClient errorClient =
                    connect(webSocketClient, TextClient.class, "/websocket/engineering")) {
                assertThat(errorClient.takeMessage()).isEqualTo("opened:engineering");
                assertThat(TextSocket.takeEvent()).startsWith("open:engineering:");

                errorClient.send("raise-error");
                assertThat(errorClient.takeMessage()).contains("error:", "deliberate error");
                assertThat(TextSocket.takeEvent()).isEqualTo("message:engineering:raise-error");
                assertThat(TextSocket.takeEvent()).contains("error:", "deliberate error");
            }
        }
    }

    @Test
    @Timeout(55)
    void exchangesJsonObjectsThroughTypedWebSocketHandlers() throws Exception {
        try (EmbeddedServer server = startServer();
                WebSocketClient webSocketClient = createClient(server);
                JsonClient client = connect(webSocketClient, JsonClient.class, "/websocket-json")) {
            client.send(Map.of("item", "notebook", "quantity", 3));

            assertThat(client.takeMessage())
                    .containsEntry("status", "accepted")
                    .containsEntry("item", "notebook")
                    .containsEntry("quantity", 3);
        }
    }

    private static EmbeddedServer startServer() {
        return ApplicationContext.run(
                EmbeddedServer.class, Map.of("micronaut.server.port", -1), Environment.TEST);
    }

    private static WebSocketClient createClient(EmbeddedServer server) {
        ApplicationContext context = server.getApplicationContext();
        WebSocketClientRegistry<?> registry = context.getBean(WebSocketClientRegistry.class);
        return registry.resolveWebSocketClient(
                null, LoadBalancer.fixed(server.getURI()), clientConfiguration(), context);
    }

    private static DefaultHttpClientConfiguration clientConfiguration() {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setConnectTimeout(CLIENT_TIMEOUT);
        configuration.setReadTimeout(CLIENT_TIMEOUT);
        configuration.setRequestTimeout(CLIENT_TIMEOUT);
        return configuration;
    }

    private static <T extends AutoCloseable> T connect(
            WebSocketClient client, Class<T> clientType, String path) throws Exception {
        CompletableFuture<T> connected = new CompletableFuture<>();
        client.connect(clientType, path).subscribe(new SingleValueSubscriber<>(connected));
        return connected.get(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @ServerWebSocket("/websocket/{room}")
    public static final class TextSocket {
        private static final BlockingQueue<String> EVENTS = new LinkedBlockingQueue<>();

        static void clearEvents() {
            EVENTS.clear();
        }

        static String takeEvent() throws InterruptedException {
            return EVENTS.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @OnOpen
        public void onOpen(String room, WebSocketSession session) {
            EVENTS.add("open:" + room + ":" + session.getId());
            session.sendAsync("opened:" + room, MediaType.TEXT_PLAIN_TYPE);
        }

        @OnMessage
        public void onMessage(String message, String room, WebSocketSession session) {
            EVENTS.add("message:" + room + ":" + message);
            if (message.equals("raise-error")) {
                throw new IllegalArgumentException("deliberate error");
            }
            session.sendAsync(room + ":" + message.toUpperCase(Locale.ROOT), MediaType.TEXT_PLAIN_TYPE);
        }

        @OnError
        public void onError(Throwable error, WebSocketSession session) {
            EVENTS.add("error:" + error.getMessage());
            session.sendAsync("error:" + error.getMessage(), MediaType.TEXT_PLAIN_TYPE);
        }

        @OnClose
        public void onClose(String room, WebSocketSession session, CloseReason reason) {
            EVENTS.add("close:" + room + ":" + reason.getCode() + ":" + session.getId());
        }
    }

    @ClientWebSocket("/websocket/{room}")
    public abstract static class TextClient implements AutoCloseable {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<CloseReason> closeReasons = new LinkedBlockingQueue<>();
        private WebSocketSession session;

        @OnOpen
        public void onOpen(WebSocketSession session) {
            this.session = session;
        }

        @OnMessage
        public void onMessage(String message) {
            messages.add(message);
        }

        @OnClose
        public void onClose(CloseReason reason) {
            closeReasons.add(reason);
        }

        @OnError
        public void onError(Throwable error) {
            messages.add("client-error:" + error.getMessage());
        }

        @Produces(MediaType.TEXT_PLAIN)
        public abstract void send(String message);

        String takeMessage() throws InterruptedException {
            return messages.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        CloseReason takeCloseReason() throws InterruptedException {
            return closeReasons.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            if (session != null && session.isOpen()) {
                session.close(CloseReason.NORMAL);
            }
        }
    }

    @ServerWebSocket("/websocket-json")
    public static final class JsonSocket {
        @OnMessage
        public void onMessage(Map<String, Object> message, WebSocketSession session) {
            session.sendAsync(
                    Map.of(
                            "status", "accepted",
                            "item", message.get("item"),
                            "quantity", message.get("quantity")),
                    MediaType.APPLICATION_JSON_TYPE);
        }
    }

    @ClientWebSocket("/websocket-json")
    public abstract static class JsonClient implements AutoCloseable {
        private final BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();
        private WebSocketSession session;

        @OnOpen
        public void onOpen(WebSocketSession session) {
            this.session = session;
        }

        @OnMessage
        public void onMessage(Map<String, Object> message) {
            messages.add(message);
        }

        public abstract void send(Map<String, Object> message);

        Map<String, Object> takeMessage() throws InterruptedException {
            return messages.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            if (session != null && session.isOpen()) {
                session.close(CloseReason.NORMAL);
            }
        }
    }

    private static final class SingleValueSubscriber<T> implements Subscriber<T> {
        private final CompletableFuture<T> result;

        private SingleValueSubscriber(CompletableFuture<T> result) {
            this.result = result;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(T value) {
            result.complete(value);
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            if (!result.isDone()) {
                result.completeExceptionally(
                        new IllegalStateException("WebSocket connection completed without a client"));
            }
        }
    }
}
