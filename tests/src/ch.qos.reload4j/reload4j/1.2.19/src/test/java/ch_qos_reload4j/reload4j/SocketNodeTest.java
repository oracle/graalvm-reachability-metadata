/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketNode;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketNodeTest {
    private static final String LOGGER_NAME = "reload4j.socket-node";

    @Test
    void readsSerializedLoggingEventAndDispatchesItToRepositoryLogger() throws Exception {
        Logger logger = Logger.getLogger(LOGGER_NAME);
        RecordingAppender appender = new RecordingAppender();
        logger.setLevel(Level.INFO);
        logger.setAdditivity(false);
        logger.addAppender(appender);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<?> socketNodeRun = executorService.submit(() -> {
                runSocketNode(serverSocket);
                return null;
            });
            LoggingEvent sentEvent = new LoggingEvent(
                    SocketNodeTest.class.getName(),
                    logger,
                    Level.ERROR,
                    "message received by SocketNode",
                    null);

            writeSingleLoggingEvent(serverSocket.getLocalPort(), sentEvent);

            LoggingEvent receivedEvent = appender.awaitEvent();
            assertThat(receivedEvent.getLoggerName()).isEqualTo(LOGGER_NAME);
            assertThat(receivedEvent.getLevel()).isSameAs(Level.ERROR);
            assertThat(receivedEvent.getRenderedMessage()).isEqualTo("message received by SocketNode");
            socketNodeRun.get(5, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            logger.removeAppender(appender);
            logger.setLevel(null);
            logger.setAdditivity(true);
            appender.close();
        }
    }

    private static void runSocketNode(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept()) {
            new SocketNode(socket, LogManager.getLoggerRepository()).run();
        }
    }

    private static void writeSingleLoggingEvent(int port, LoggingEvent event) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
            outputStream.writeObject(event);
            outputStream.flush();
        }
    }

    private static final class RecordingAppender extends AppenderSkeleton {
        private final CountDownLatch eventReceived = new CountDownLatch(1);
        private final AtomicReference<LoggingEvent> event = new AtomicReference<>();

        @Override
        protected void append(LoggingEvent loggingEvent) {
            event.set(loggingEvent);
            eventReceived.countDown();
        }

        private LoggingEvent awaitEvent() throws InterruptedException {
            assertThat(eventReceived.await(5, TimeUnit.SECONDS)).isTrue();
            return event.get();
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
