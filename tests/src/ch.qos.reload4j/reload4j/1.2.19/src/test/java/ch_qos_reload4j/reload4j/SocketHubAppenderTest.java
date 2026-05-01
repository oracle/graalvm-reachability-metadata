/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketHubAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class SocketHubAppenderTest {
    private static final Logger LOGGER = Logger.getLogger(SocketHubAppenderTest.class);
    private static final String MESSAGE = "socket hub appender serialized event";

    @Test
    void appendSendsSerializedLoggingEventToConnectedClient() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        LoopbackSocketHubAppender appender = new LoopbackSocketHubAppender();
        try {
            appender.setName("socket-hub-appender-test");
            appender.setApplication("socket-hub-application");
            appender.setLocationInfo(true);
            appender.setPort(0);
            appender.activateOptions();

            int port = appender.awaitPort();
            CompletableFuture<Void> streamReady = new CompletableFuture<>();
            Future<LoggingEvent> receivedEvent = executor.submit(() -> readLoggingEvent(port, streamReady));
            streamReady.get(10, TimeUnit.SECONDS);

            LoggingEvent event = new LoggingEvent(SocketHubAppenderTest.class.getName(), LOGGER, Level.INFO, MESSAGE,
                    null);
            appendUntilReceived(appender, event, receivedEvent);

            LoggingEvent restored = receivedEvent.get(10, TimeUnit.SECONDS);
            assertThat(restored.getLoggerName()).isEqualTo(LOGGER.getName());
            assertThat(restored.getLevel()).isEqualTo(Level.INFO);
            assertThat(restored.getMessage()).isEqualTo(MESSAGE);
            assertThat(restored.getProperty("application")).isEqualTo("socket-hub-application");
            assertThat(restored.getThreadName()).isEqualTo(Thread.currentThread().getName());
            assertThat(restored.getLocationInformation().fullInfo).isNotBlank();
        } finally {
            appender.close();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static LoggingEvent readLoggingEvent(int port, CompletableFuture<Void> streamReady) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            streamReady.complete(null);
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(LoggingEvent.class);
            return (LoggingEvent) restored;
        }
    }

    private static void appendUntilReceived(SocketHubAppender appender, LoggingEvent event,
            Future<LoggingEvent> receivedEvent) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!receivedEvent.isDone() && System.nanoTime() < deadline) {
            appender.append(event);
            Thread.sleep(25);
        }
    }

    private static final class LoopbackSocketHubAppender extends SocketHubAppender {
        private final CompletableFuture<Integer> port = new CompletableFuture<>();

        int awaitPort() throws Exception {
            return port.get(10, TimeUnit.SECONDS);
        }

        @Override
        protected ServerSocket createServerSocket(int socketPort) throws IOException {
            ServerSocket serverSocket = new ServerSocket(socketPort, 50, InetAddress.getLoopbackAddress());
            port.complete(serverSocket.getLocalPort());
            return serverSocket;
        }
    }
}
