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
    private static final String MESSAGE = "socket appender serialized event";

    @Test
    void appendSendsSerializedLoggingEventToRemoteServer() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopback)) {
            Future<LoggingEvent> receivedEvent = executor.submit(() -> readLoggingEvent(serverSocket));
            SocketAppender appender = new SocketAppender(loopback, serverSocket.getLocalPort());
            try {
                appender.setApplication("socket-appender-test");
                appender.setLocationInfo(true);

                LoggingEvent event = new LoggingEvent(SocketAppenderTest.class.getName(), LOGGER, Level.INFO, MESSAGE,
                        null);
                appender.append(event);

                LoggingEvent restored = receivedEvent.get(10, TimeUnit.SECONDS);
                assertThat(restored.getLoggerName()).isEqualTo(LOGGER.getName());
                assertThat(restored.getLevel()).isEqualTo(Level.INFO);
                assertThat(restored.getMessage()).isEqualTo(MESSAGE);
                assertThat(restored.getProperty("application")).isEqualTo("socket-appender-test");
                assertThat(restored.getThreadName()).isEqualTo(Thread.currentThread().getName());
                assertThat(restored.getLocationInformation().fullInfo).isNotBlank();
            } finally {
                appender.close();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static LoggingEvent readLoggingEvent(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept();
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(LoggingEvent.class);
            return (LoggingEvent) restored;
        }
    }
}
