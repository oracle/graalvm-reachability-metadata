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
import java.util.concurrent.CountDownLatch;
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
    private static final String LOGGER_FQCN = SocketHubAppenderTest.class.getName();

    @Test
    void broadcastsSerializedLoggingEventToConnectedClient() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reload4j-socket-hub-appender-test");
            thread.setDaemon(true);
            return thread;
        });
        LoopbackSocketHubAppender appender = new LoopbackSocketHubAppender();
        appender.setName("socket-hub-appender-test");
        appender.setPort(0);
        appender.setApplication("socket-hub-application");
        appender.setLocationInfo(true);
        appender.activateOptions();

        try {
            int port = appender.awaitBoundPort();
            Future<LoggingEvent> receivedEvent = executor.submit(() -> readLoggingEvent(port));
            LoggingEvent event = new LoggingEvent(LOGGER_FQCN, LOGGER, Level.INFO, "socket-hub-message", null);

            LoggingEvent received = waitForBroadcast(appender, event, receivedEvent);

            assertThat(received.getLoggerName()).isEqualTo(LOGGER.getName());
            assertThat(received.getLevel()).isEqualTo(Level.INFO);
            assertThat(received.getMessage()).isEqualTo("socket-hub-message");
            assertThat(received.getProperty("application")).isEqualTo("socket-hub-application");
        } finally {
            appender.close();
            executor.shutdownNow();
        }
    }

    private static LoggingEvent waitForBroadcast(SocketHubAppender appender, LoggingEvent event,
            Future<LoggingEvent> receivedEvent) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!receivedEvent.isDone() && System.nanoTime() < deadline) {
            appender.doAppend(event);
            Thread.sleep(50);
        }
        return receivedEvent.get(5, TimeUnit.SECONDS);
    }

    private static LoggingEvent readLoggingEvent(int port) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            return (LoggingEvent) input.readObject();
        }
    }

    private static final class LoopbackSocketHubAppender extends SocketHubAppender {
        private final CountDownLatch serverReady = new CountDownLatch(1);
        private volatile int boundPort;

        @Override
        protected ServerSocket createServerSocket(int socketPort) throws IOException {
            ServerSocket serverSocket = new ServerSocket(socketPort, 1, InetAddress.getLoopbackAddress());
            boundPort = serverSocket.getLocalPort();
            serverReady.countDown();
            return serverSocket;
        }

        int awaitBoundPort() throws InterruptedException {
            assertThat(serverReady.await(5, TimeUnit.SECONDS)).as("SocketHubAppender server started").isTrue();
            return boundPort;
        }
    }
}
