/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketHubAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketHubAppenderInnerServerMonitorTest {
    private static final String LOGGER_NAME = "reload4j.socket-hub-appender.server-monitor";

    @Test
    void sendsCachedEventsToNewlyAcceptedSocketClient() throws Exception {
        TestSocketHubAppender appender = new TestSocketHubAppender();
        try {
            appender.setName("socket-hub-appender-server-monitor-test");
            appender.setApplication("cached-event-application");
            appender.setBufferSize(2);
            appender.setPort(0);
            appender.activateOptions();

            LoggingEvent cachedEvent = new LoggingEvent(
                    SocketHubAppenderInnerServerMonitorTest.class.getName(),
                    Logger.getLogger(LOGGER_NAME),
                    Level.ERROR,
                    "cached message sent to a late SocketHubAppender client",
                    new IllegalStateException("cached throwable"));
            appender.append(cachedEvent);

            LoggingEvent receivedEvent = readSingleCachedEvent(appender.awaitPort());

            assertThat(receivedEvent.getLoggerName()).isEqualTo(LOGGER_NAME);
            assertThat(receivedEvent.getLevel()).isSameAs(Level.ERROR);
            assertThat(receivedEvent.getRenderedMessage())
                    .isEqualTo("cached message sent to a late SocketHubAppender client");
            assertThat(receivedEvent.getProperty("application")).isEqualTo("cached-event-application");
            assertThat(receivedEvent.getThrowableStrRep()).isNotEmpty();
        } finally {
            appender.close();
        }
    }

    private static LoggingEvent readSingleCachedEvent(int port) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5_000);
            socket.setSoTimeout(5_000);
            try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
                Object receivedObject = inputStream.readObject();
                assertThat(receivedObject).isInstanceOf(LoggingEvent.class);
                return (LoggingEvent) receivedObject;
            }
        }
    }

    private static final class TestSocketHubAppender extends SocketHubAppender {
        private final CountDownLatch serverStarted = new CountDownLatch(1);
        private volatile int localPort;

        @Override
        protected ServerSocket createServerSocket(int socketPort) throws IOException {
            ServerSocket serverSocket = new ServerSocket(socketPort, 1, InetAddress.getLoopbackAddress());
            localPort = serverSocket.getLocalPort();
            serverStarted.countDown();
            return serverSocket;
        }

        private int awaitPort() throws InterruptedException {
            assertThat(serverStarted.await(5, TimeUnit.SECONDS)).isTrue();
            return localPort;
        }
    }
}
