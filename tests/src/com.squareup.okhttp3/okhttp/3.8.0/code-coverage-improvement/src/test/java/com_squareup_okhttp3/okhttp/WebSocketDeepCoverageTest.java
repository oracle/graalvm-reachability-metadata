/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.Buffer;
import org.junit.jupiter.api.Test;

public class WebSocketDeepCoverageTest {
    @Test
    void publicWebSocketFactoryCompletesHandshakeAndWritesCloseFrame() throws Exception {
        ServerSocket server = new ServerSocket(0);
        CountDownLatch closeFrame = new CountDownLatch(1);
        Thread serverThread = new Thread(() -> serveHandshake(server, closeFrame),
                "coverage-websocket-server");
        serverThread.start();
        CountDownLatch opened = new CountDownLatch(1);
        OkHttpClient client = new OkHttpClient();
        WebSocket socket = client.newWebSocket(new Request.Builder()
                .url("http://localhost:" + server.getLocalPort() + "/socket").build(),
                new WebSocketListener() {
                    @Override public void onOpen(WebSocket webSocket, Response response) {
                        opened.countDown();
                        webSocket.send("message");
                        webSocket.close(1000, "done");
                    }
                });
        assertThat(opened.await(2L, TimeUnit.SECONDS)).isTrue();
        assertThat(closeFrame.await(2L, TimeUnit.SECONDS)).isTrue();
        socket.cancel();
        client.dispatcher().executorService().shutdown();
        server.close();
        serverThread.join(2000L);
    }

    @Test
    void websocketReaderProcessesControlFramesAndFragmentedMessages() throws Exception {
        Request request = new Request.Builder().url("http://localhost/socket").build();
        RecordingListener listener = new RecordingListener();
        RealWebSocket webSocket = new RealWebSocket(request, listener, new Random(1L));
        Buffer input = new Buffer();
        Buffer output = new Buffer();
        input.writeByte(0x89).writeByte(4).writeUtf8("ping");
        input.writeByte(0x01).writeByte(2).writeUtf8("he");
        input.writeByte(0x89).writeByte(1).writeUtf8("x");
        input.writeByte(0x80).writeByte(3).writeUtf8("llo");
        input.writeByte(0x88).writeByte(5).writeShort(1000).writeUtf8("bye");
        RealWebSocket.Streams streams = new RealWebSocket.Streams(true, input, output) {
            @Override public void close() {
            }
        };

        webSocket.initReaderAndWriter("coverage", 0L, streams);
        webSocket.loopReader();
        while (webSocket.writeOneFrame()) {
            // Drain pong frames synchronously so the in-memory sink is stable on native.
        }

        assertThat(listener.text).isEqualTo("hello");
        assertThat(listener.closeCode).isEqualTo(1000);
        assertThat(webSocket.queueSize()).isZero();
        assertThat(output.size()).isGreaterThan(0L);
    }

    private static void serveHandshake(ServerSocket server, CountDownLatch closeFrame) {
        try (Socket socket = server.accept()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
            String key = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "Sec-WebSocket-Key:", 0, 18)) {
                    key = line.substring(19).trim();
                }
            }
            String accept = okio.ByteString.encodeUtf8(key
                    + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").sha1().base64();
            OutputStream output = socket.getOutputStream();
            output.write(("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.flush();
            InputStream input = socket.getInputStream();
            while (true) {
                int first = input.read();
                int second = input.read();
                if (first == -1 || second == -1) {
                    return;
                }
                int length = second & 0x7f;
                if (length == 126) {
                    length = (input.read() << 8) | input.read();
                } else if (length == 127) {
                    throw new IOException("unexpected large frame");
                }
                if ((second & 0x80) != 0) {
                    length += 4;
                }
                byte[] frame = new byte[length];
                int offset = 0;
                while (offset < frame.length) {
                    int read = input.read(frame, offset, frame.length - offset);
                    if (read == -1) {
                        return;
                    }
                    offset += read;
                }
                if ((first & 0x0f) == 8) {
                    closeFrame.countDown();
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static final class RecordingListener extends WebSocketListener {
        private String text;
        private int closeCode;

        @Override public void onMessage(WebSocket webSocket, String message) {
            text = message;
        }

        @Override public void onClosing(WebSocket webSocket, int code, String reason) {
            closeCode = code;
        }

        @Override public void onClosed(WebSocket webSocket, int code, String reason) {
        }
    }
}
