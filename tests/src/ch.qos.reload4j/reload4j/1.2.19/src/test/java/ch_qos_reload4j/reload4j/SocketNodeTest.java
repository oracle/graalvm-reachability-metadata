/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketNode;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class SocketNodeTest {
    private static final String LOGGER_NAME = SocketNodeTest.class.getName() + ".remote";
    private static final String MESSAGE = "socket node deserialized event";

    @AfterEach
    void resetRepository() {
        LogManager.resetConfiguration();
    }

    @Test
    void runReadsSerializedLoggingEventAndDispatchesItToRepositoryLogger() throws Exception {
        Logger logger = Logger.getLogger(LOGGER_NAME);
        CapturingAppender appender = new CapturingAppender();
        logger.removeAllAppenders();
        logger.setAdditivity(false);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        InetAddress loopback = InetAddress.getLoopbackAddress();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopback)) {
            Future<Void> socketNodeRun = executor.submit(() -> runSocketNode(serverSocket));

            sendLoggingEvent(loopback, serverSocket.getLocalPort());

            assertThat(appender.awaitEvent()).isTrue();
            socketNodeRun.get(10, TimeUnit.SECONDS);
            assertThat(appender.lastEvent).isNotNull();
            assertThat(appender.lastEvent.getLoggerName()).isEqualTo(LOGGER_NAME);
            assertThat(appender.lastEvent.getLevel()).isEqualTo(Level.INFO);
            assertThat(appender.lastEvent.getMessage()).isEqualTo(MESSAGE);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Void runSocketNode(ServerSocket serverSocket) throws Exception {
        Socket socket = serverSocket.accept();
        SocketNode socketNode = new SocketNode(socket, LogManager.getLoggerRepository());
        socketNode.run();
        return null;
    }

    private static void sendLoggingEvent(InetAddress host, int port) throws Exception {
        try (Socket socket = new Socket(host, port);
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            LoggingEvent event = new LoggingEvent(SocketNodeTest.class.getName(), Logger.getLogger(LOGGER_NAME),
                    Level.INFO, MESSAGE, null);
            output.writeObject(event);
            output.flush();
        }
    }

    private static final class CapturingAppender extends AppenderSkeleton {
        private final CountDownLatch eventReceived = new CountDownLatch(1);
        private volatile LoggingEvent lastEvent;

        boolean awaitEvent() throws InterruptedException {
            return eventReceived.await(10, TimeUnit.SECONDS);
        }

        @Override
        protected void append(LoggingEvent event) {
            lastEvent = event;
            eventReceived.countDown();
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
