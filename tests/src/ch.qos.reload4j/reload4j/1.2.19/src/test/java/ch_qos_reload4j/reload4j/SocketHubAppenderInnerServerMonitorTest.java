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

public class SocketHubAppenderInnerServerMonitorTest {
    private static final Logger LOGGER = Logger.getLogger(SocketHubAppenderInnerServerMonitorTest.class);
    private static final String LOGGER_FQCN = SocketHubAppenderInnerServerMonitorTest.class.getName();

    @Test
    void sendsCachedEventsToNewClientWhenBufferIsConfigured() throws Exception {
        LoopbackSocketHubAppender appender = new LoopbackSocketHubAppender();
        appender.setName("socket-hub-server-monitor-test");
        appender.setPort(0);
        appender.setBufferSize(2);
        appender.activateOptions();

        try {
            int port = appender.awaitBoundPort();
            LoggingEvent cachedEvent = new LoggingEvent(LOGGER_FQCN, LOGGER, Level.WARN,
                    "cached socket hub event", null);
            appender.doAppend(cachedEvent);

            LoggingEvent received = readCachedLoggingEvent(port);

            assertThat(received.getLoggerName()).isEqualTo(LOGGER.getName());
            assertThat(received.getLevel()).isEqualTo(Level.WARN);
            assertThat(received.getMessage()).isEqualTo("cached socket hub event");
        } finally {
            appender.close();
        }
    }

    private static LoggingEvent readCachedLoggingEvent(int port) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5_000);
            socket.setSoTimeout(5_000);
            try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                return (LoggingEvent) input.readObject();
            }
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
