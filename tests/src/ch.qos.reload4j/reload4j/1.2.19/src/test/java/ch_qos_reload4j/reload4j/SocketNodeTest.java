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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
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
import org.junit.jupiter.api.Test;

public class SocketNodeTest {
    private static final String LOGGER_FQCN = SocketNodeTest.class.getName();

    @Test
    void readsSerializedLoggingEventAndDispatchesToRepositoryLogger() throws Exception {
        Logger receivingLogger = Logger.getLogger("reload4j.socket-node." + UUID.randomUUID());
        Level originalLevel = receivingLogger.getLevel();
        boolean originalAdditivity = receivingLogger.getAdditivity();
        CapturingAppender appender = new CapturingAppender();
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "reload4j-socket-node-test");
            thread.setDaemon(true);
            return thread;
        });

        receivingLogger.setAdditivity(false);
        receivingLogger.setLevel(Level.INFO);
        receivingLogger.addAppender(appender);
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<Socket> acceptedSocket = executor.submit(serverSocket::accept);
            try (Socket clientSocket = new Socket()) {
                clientSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()),
                        5_000);
                Socket socketNodeSocket = acceptedSocket.get(5, TimeUnit.SECONDS);
                Future<?> socketNodeRun;
                try (ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream())) {
                    output.flush();
                    socketNodeRun = executor.submit(new SocketNode(socketNodeSocket, LogManager.getLoggerRepository()));
                    output.writeObject(new LoggingEvent(LOGGER_FQCN, receivingLogger, Level.WARN, "socket-node-message",
                            null));
                    output.flush();

                    LoggingEvent received = appender.awaitEvent();

                    assertThat(received.getLoggerName()).isEqualTo(receivingLogger.getName());
                    assertThat(received.getLevel()).isEqualTo(Level.WARN);
                    assertThat(received.getMessage()).isEqualTo("socket-node-message");
                }
                socketNodeRun.get(5, TimeUnit.SECONDS);
            }
        } finally {
            receivingLogger.removeAppender(appender);
            receivingLogger.setLevel(originalLevel);
            receivingLogger.setAdditivity(originalAdditivity);
            executor.shutdownNow();
        }
    }

    private static final class CapturingAppender extends AppenderSkeleton {
        private final CountDownLatch eventReceived = new CountDownLatch(1);
        private volatile LoggingEvent event;

        @Override
        protected void append(LoggingEvent event) {
            this.event = event;
            eventReceived.countDown();
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }

        private LoggingEvent awaitEvent() throws InterruptedException {
            assertThat(eventReceived.await(5, TimeUnit.SECONDS)).as("SocketNode dispatched the logging event").isTrue();
            return event;
        }
    }
}
