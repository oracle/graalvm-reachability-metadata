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
import ch.qos.logback.classic.net.server.ServerSocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.AppenderBase;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ServerSocketFactory;
import org.junit.jupiter.api.Test;

public class RemoteAppenderStreamClientTest {

    @Test
    void readsSerializedEventsThroughTheServerSocketReceiverPipeline() throws Exception {
        LoggerContext context = new LoggerContext();
        context.start();

        CapturingAppender appender = new CapturingAppender();
        appender.setContext(context);
        appender.start();

        Logger targetLogger = context.getLogger("remote-appender-stream-client-target");
        targetLogger.setAdditive(false);
        targetLogger.addAppender(appender);

        PreparedServerSocketReceiver receiver = null;
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            receiver = new PreparedServerSocketReceiver(serverSocket);
            receiver.setContext(context);
            receiver.setPort(serverSocket.getLocalPort());
            receiver.setAddress(InetAddress.getLoopbackAddress().getHostAddress());
            receiver.start();

            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
                 ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
                outputStream.writeObject(createEvent(context, targetLogger.getName(), "remote appender stream client message"));
                outputStream.flush();
            }

            assertThat(appender.awaitFormattedMessage()).isEqualTo("remote appender stream client message");
        } finally {
            if (receiver != null) {
                receiver.stop();
            }
            targetLogger.detachAppender(appender);
            context.stop();
        }
    }

    private static LoggingEventVO createEvent(LoggerContext context, String loggerName, String message) {
        Logger logger = context.getLogger(loggerName);
        LoggingEvent event = new LoggingEvent(
                RemoteAppenderStreamClientTest.class.getName(),
                logger,
                Level.INFO,
                message,
                null,
                null
        );
        return LoggingEventVO.build(event);
    }

    private static final class PreparedServerSocketReceiver extends ServerSocketReceiver {

        private final ServerSocketFactory serverSocketFactory;

        private PreparedServerSocketReceiver(ServerSocket serverSocket) {
            this.serverSocketFactory = new PreparedServerSocketFactory(serverSocket);
        }

        @Override
        protected ServerSocketFactory getServerSocketFactory() {
            return serverSocketFactory;
        }
    }

    private static final class PreparedServerSocketFactory extends ServerSocketFactory {

        private final ServerSocket serverSocket;
        private boolean used;

        private PreparedServerSocketFactory(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        @Override
        public ServerSocket createServerSocket(int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
            if (used) {
                throw new IllegalStateException("Server socket already created");
            }
            used = true;
            return serverSocket;
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
