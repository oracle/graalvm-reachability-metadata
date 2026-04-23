/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SimpleSocketServer;
import ch.qos.logback.classic.net.SocketNode;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.AppenderBase;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class SocketNodeTest {

    @Test
    void readsSerializedLoggingEventsFromTheSocketStream() throws Exception {
        LoggerContext context = new LoggerContext();
        context.start();

        CapturingAppender appender = new CapturingAppender();
        appender.setContext(context);
        appender.start();

        Logger targetLogger = context.getLogger("socket-node-target");
        targetLogger.setAdditive(false);
        targetLogger.addAppender(appender);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<?> sender = executor.submit(() -> {
                sendEvent(serverSocket.getLocalPort(), createEvent(context, targetLogger.getName(), "socket node message"));
                return null;
            });

            try (Socket socket = serverSocket.accept()) {
                TrackingSocketServer socketServer = new TrackingSocketServer(context);
                SocketNode socketNode = new SocketNode(socketServer, socket, context);

                socketNode.run();
                sender.get(10, TimeUnit.SECONDS);

                assertThat(appender.awaitFormattedMessage()).isEqualTo("socket node message");
                assertThat(socketServer.isClosingNotified()).isTrue();
            }
        } finally {
            executor.shutdownNow();
            targetLogger.detachAppender(appender);
            context.stop();
        }
    }

    private static LoggingEventVO createEvent(LoggerContext context, String loggerName, String message) {
        Logger logger = context.getLogger(loggerName);
        LoggingEvent event = new LoggingEvent(SocketNodeTest.class.getName(), logger, Level.INFO, message, null, null);
        return LoggingEventVO.build(event);
    }

    private static void sendEvent(int port, LoggingEventVO event) {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
             ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
            outputStream.writeObject(event);
            outputStream.flush();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class TrackingSocketServer extends SimpleSocketServer {

        private boolean closingNotified;

        private TrackingSocketServer(LoggerContext context) {
            super(context, 0);
        }

        @Override
        public void socketNodeClosing(SocketNode socketNode) {
            closingNotified = true;
        }

        private boolean isClosingNotified() {
            return closingNotified;
        }
    }

    private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

        private final ArrayBlockingQueue<ILoggingEvent> events = new ArrayBlockingQueue<>(1);

        @Override
        protected void append(ILoggingEvent eventObject) {
            events.offer(eventObject);
        }

        private String awaitFormattedMessage() throws InterruptedException {
            ILoggingEvent event = events.poll(10, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            return event.getFormattedMessage();
        }
    }
}
