/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class SocketAppenderTest {
    private static final Logger LOGGER = Logger.getLogger(SocketAppenderTest.class);
    private static final String LOGGER_FQCN = SocketAppenderTest.class.getName();

    @Test
    void sendsSerializedLoggingEventToSocketServer() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reload4j-socket-appender-test");
            thread.setDaemon(true);
            return thread;
        });

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<LoggingEvent> receivedEvent = executor.submit(() -> readLoggingEvent(serverSocket));
            SocketAppender appender = new SocketAppender(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
            appender.setName("socket-appender-test");
            appender.setApplication("socket-appender-application");
            appender.setLocationInfo(true);

            try {
                appender.doAppend(new LoggingEvent(LOGGER_FQCN, LOGGER, Level.INFO, "socket-appender-message", null));

                LoggingEvent event = receivedEvent.get(5, TimeUnit.SECONDS);

                assertThat(event.getLoggerName()).isEqualTo(LOGGER.getName());
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getMessage()).isEqualTo("socket-appender-message");
                assertThat(event.getProperty("application")).isEqualTo("socket-appender-application");
            } finally {
                appender.close();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static LoggingEvent readLoggingEvent(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept();
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            return (LoggingEvent) input.readObject();
        }
    }
}
