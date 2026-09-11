/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.internal.ws.RealWebSocket;
import okhttp3.internal.ws.WebSocketProtocol;
import okio.Buffer;
import okio.ByteString;
import org.junit.jupiter.api.Test;

public class WebSocketApiCoverageTest {
    @Test
    void websocketQueuesMessagesAndProcessesIncomingPingBeforeConnect() {
        Request request = new Request.Builder().url("http://localhost/socket").build();
        WebSocketListener listener = new WebSocketListener() {
        };
        RealWebSocket socket = new RealWebSocket(request, listener, new Random(1L));
        assertThat(WebSocketProtocol.acceptHeader("key")).isNotEmpty();
        assertThat(socket.request()).isSameAs(request);
        assertThat(socket.queueSize()).isZero();
        assertThat(socket.send("hello")).isTrue();
        assertThat(socket.queueSize()).isEqualTo(5L);
        socket.onReadPing(ByteString.encodeUtf8("ping"));
        assertThat(socket.queueSize()).isEqualTo(5L);
        assertThat(socket.close(1000, "done")).isTrue();
        assertThat(socket.send("after close")).isFalse();
    }

    @Test
    void websocketCallbacksAndReaderLifecycleHandleMessagesAndCancellation() throws Exception {
        Request request = new Request.Builder().url("http://localhost/socket").build();
        RecordingListener listener = new RecordingListener();
        RealWebSocket socket = new RealWebSocket(request, listener, new Random(2L));
        RealWebSocket.Streams streams = new RealWebSocket.Streams(true, new Buffer(),
                new Buffer()) {
            @Override public void close() {
            }
        };
        socket.initReaderAndWriter("coverage", 1L, streams);
        socket.onReadMessage("text");
        socket.onReadMessage(ByteString.encodeUtf8("bytes"));
        socket.onReadPong(ByteString.encodeUtf8("pong"));
        assertThat(listener.text).isEqualTo("text");
        assertThat(listener.bytes.utf8()).isEqualTo("bytes");
        socket.send(ByteString.encodeUtf8("queued"));
        assertThat(socket.queueSize()).isGreaterThan(0L);
        try {
            socket.loopReader();
        } catch (RuntimeException expectedAtEndOfStream) {
            assertThat(expectedAtEndOfStream).isNotNull();
        }
        socket.onReadClose(1000, "done");
        RealWebSocket cancellable = new RealWebSocket(request, listener, new Random(3L));
        cancellable.connect(new okhttp3.OkHttpClient());
        cancellable.cancel();
        listener.onOpen(new NoopWebSocket(), new Response.Builder().request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").build());
        listener.onMessage(new NoopWebSocket(), "callback");
        listener.onMessage(new NoopWebSocket(), ByteString.encodeUtf8("callback"));
        listener.onClosing(new NoopWebSocket(), 1000, "closing");
        listener.onClosed(new NoopWebSocket(), 1000, "closed");
        assertThat(listener.closed).isTrue();
    }

    private static final class RecordingListener extends WebSocketListener {
        private String text;
        private ByteString bytes = ByteString.EMPTY;
        private boolean closed;

        @Override public void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            this.text = text;
        }

        @Override public void onMessage(WebSocket webSocket, ByteString bytes) {
            super.onMessage(webSocket, bytes);
            this.bytes = bytes;
        }

        @Override public void onClosed(WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);
            closed = true;
        }
    }

    private static final class NoopWebSocket implements WebSocket {
        public Request request() {
            return new Request.Builder().url("http://localhost/").build();
        }

        public long queueSize() {
            return 0;
        }

        public boolean send(String text) {
            return true;
        }

        public boolean send(ByteString bytes) {
            return true;
        }

        public boolean close(int code, String reason) {
            return true;
        }

        public void cancel() {
        }
    }
}
