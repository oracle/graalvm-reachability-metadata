/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@WebSocket
public class WebSocketClientEndpoint {
    private final CountDownLatch stringMessageLatch = new CountDownLatch(1);
    private final AtomicReference<String> stringMessage = new AtomicReference<>();
    private final CountDownLatch binaryMessageLatch = new CountDownLatch(1);
    private final AtomicReference<byte[]> binaryMessage = new AtomicReference<>();

    @OnWebSocketMessage
    public void onTextMessage(Session session, String message) {
        stringMessage.set(message);
        stringMessageLatch.countDown();
    }

    //    @OnWebSocketMessage
    public void onBinaryMessage(byte[] message, int offset, int length) {
        byte[] data = new byte[length];
        System.arraycopy(message, offset, data, 0, length);
        binaryMessage.set(data);
        binaryMessageLatch.countDown();
    }

    String awaitStringMessage() throws InterruptedException {
        if (!stringMessageLatch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Failed to await string message in time");
        }
        return stringMessage.get();
    }

    byte[] awaitBinaryMessage() throws InterruptedException {
        if (!binaryMessageLatch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Failed to await binary message in time");
        }
        return binaryMessage.get();
    }

}
