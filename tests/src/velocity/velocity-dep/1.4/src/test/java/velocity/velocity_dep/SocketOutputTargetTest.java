/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log.LogEvent;
import org.apache.log.Priority;
import org.apache.log.output.net.SocketOutputTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class SocketOutputTargetTest {
    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

    @Test
    void serializesLogEventToSocketOutputStream() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = createLoopbackServerSocket()) {
            CompletableFuture<LogEvent> receivedEvent = CompletableFuture.supplyAsync(
                    () -> readSerializedEvent(serverSocket), executorService);
            SocketOutputTarget target = new SocketOutputTarget(
                    InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
            try {
                target.setErrorHandler((message, throwable, event) -> fail(message, throwable));

                target.processEvent(createLogEvent());

                LogEvent logEvent = receivedEvent.get(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                assertEquals("dynamic.access.socket", logEvent.getCategory());
                assertEquals("serialized socket output target event", logEvent.getMessage());
                assertSame(Priority.INFO, logEvent.getPriority());
            } finally {
                target.close();
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    private static ServerSocket createLoopbackServerSocket() throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        return serverSocket;
    }

    private static LogEvent createLogEvent() {
        LogEvent logEvent = new LogEvent();
        logEvent.setCategory("dynamic.access.socket");
        logEvent.setMessage("serialized socket output target event");
        logEvent.setPriority(Priority.INFO);
        logEvent.setTime(System.currentTimeMillis());
        return logEvent;
    }

    private static LogEvent readSerializedEvent(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
                Object value = inputStream.readObject();
                if (value instanceof LogEvent) {
                    return (LogEvent) value;
                }
                throw new AssertionError("Expected serialized LogEvent but received " + value);
            }
        } catch (ClassNotFoundException | IOException e) {
            throw new AssertionError("Failed to read serialized LogEvent", e);
        }
    }
}
