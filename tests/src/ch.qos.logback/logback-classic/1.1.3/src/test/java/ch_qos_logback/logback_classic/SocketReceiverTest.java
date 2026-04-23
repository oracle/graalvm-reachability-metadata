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
import ch.qos.logback.classic.net.SocketReceiver;
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

public class SocketReceiverTest {

    @Test
    void dispatchesSerializedLoggingEventsFromTheRemoteSocket() throws Exception {
        LoggerContext context = new LoggerContext();
        context.start();

        CapturingAppender appender = new CapturingAppender();
        appender.setContext(context);
        appender.start();

        Logger targetLogger = context.getLogger("socket-receiver-target");
        targetLogger.setAdditive(false);
        targetLogger.addAppender(appender);

        SocketReceiver receiver = new SocketReceiver();
        receiver.setContext(context);
        receiver.setRemoteHost(InetAddress.getLoopbackAddress().getHostAddress());
        receiver.setReconnectionDelay(100);
        receiver.setAcceptConnectionTimeout(1_000);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            receiver.setPort(serverSocket.getLocalPort());

            Future<?> sender = executor.submit(() -> {
                try (Socket socket = serverSocket.accept();
                     ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
                    outputStream.writeObject(createEvent(context, targetLogger.getName(), "socket receiver message"));
                    outputStream.flush();
                }
                return null;
            });

            receiver.start();

            assertThat(appender.awaitFormattedMessage()).isEqualTo("socket receiver message");
            sender.get(10, TimeUnit.SECONDS);
            receiver.stop();
        } finally {
            receiver.stop();
            executor.shutdownNow();
            targetLogger.detachAppender(appender);
            context.stop();
        }
    }

    private static LoggingEventVO createEvent(LoggerContext context, String loggerName, String message) {
        Logger logger = context.getLogger(loggerName);
        LoggingEvent event = new LoggingEvent(SocketReceiverTest.class.getName(), logger, Level.INFO, message, null, null);
        return LoggingEventVO.build(event);
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
