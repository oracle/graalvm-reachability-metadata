/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@WebSocket
public class WebSocketClientEndpoint {
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private final AtomicReference<String> received = new AtomicReference<>();

    @OnWebSocketMessage
    public void onMessage(String message) {
        received.set(message);
        countDownLatch.countDown();
    }

    String awaitMessage() throws InterruptedException {
        if (!countDownLatch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Failed to await message in time");
        }
        return received.get();
    }

}
