/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * WebSocket messaging subscribes an inventory dashboard to live stock updates.
 * The dashboard acknowledges a received revision and completes the service's graceful close.
 */
public class InventoryWebSocketFeedTest {

    private static final String SUBSCRIPTION = "{\"subscribe\":\"north-depot\"}";
    private static final String UPDATE = "{\"sku\":\"tea-green\",\"quantity\":18,\"revision\":42}";
    private static final String ACKNOWLEDGEMENT = "{\"ack\":42}";
    private static final String CLOSE_REASON = "snapshot delivered";

    @Test
    @Timeout(60)
    public void receivesAndAcknowledgesStockRevisionBeforeGracefulClose() throws Exception {
        FeedService service = new FeedService();
        Dashboard dashboard = new Dashboard();
        try (MockWebServer origin = new MockWebServer()) {
            origin.enqueue(new MockResponse().withWebSocketUpgrade(service));
            origin.start();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            WebSocket feed = null;
            try {
                Request request = new Request.Builder()
                        .url(origin.url("/inventory/live"))
                        .header("Authorization", "Bearer dashboard-token")
                        .build();
                feed = client.newWebSocket(request, dashboard);
                assertThat(feed.send(SUBSCRIPTION)).isTrue();

                assertThat(dashboard.updates.poll(10, TimeUnit.SECONDS)).isEqualTo(UPDATE);
                assertThat(feed.send(ACKNOWLEDGEMENT)).isTrue();
                assertThat(dashboard.closed.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(dashboard.failure.get()).isNull();
                assertThat(service.failure.get()).isNull();
                assertThat(dashboard.closeCode).isEqualTo(1000);
                assertThat(dashboard.closeReason).isEqualTo(CLOSE_REASON);
                assertThat(service.messages).containsExactly(SUBSCRIPTION, ACKNOWLEDGEMENT);

                RecordedRequest handshake = origin.takeRequest(10, TimeUnit.SECONDS);
                assertThat(handshake).isNotNull();
                assertThat(handshake.getMethod()).isEqualTo("GET");
                assertThat(handshake.getPath()).isEqualTo("/inventory/live");
                assertThat(handshake.getHeader("Upgrade")).isEqualToIgnoringCase("websocket");
                assertThat(handshake.getHeader("Authorization")).isEqualTo("Bearer dashboard-token");
                assertThat(origin.getRequestCount()).isEqualTo(1);
            } finally {
                if (feed != null) {
                    feed.cancel();
                }
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        }
    }

    private static final class FeedService extends WebSocketListener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            messages.add(text);
            if (SUBSCRIPTION.equals(text)) {
                webSocket.send(UPDATE);
            } else if (ACKNOWLEDGEMENT.equals(text)) {
                webSocket.close(1000, CLOSE_REASON);
            } else {
                webSocket.close(1008, "unknown inventory command");
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable cause, Response response) {
            failure.set(cause);
        }
    }

    private static final class Dashboard extends WebSocketListener {
        private final BlockingQueue<String> updates = new LinkedBlockingQueue<>();
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private int closeCode;
        private String closeReason;

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            updates.add(text);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            closeCode = code;
            closeReason = reason;
            closed.countDown();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable cause, Response response) {
            failure.set(cause);
            closed.countDown();
        }
    }
}
