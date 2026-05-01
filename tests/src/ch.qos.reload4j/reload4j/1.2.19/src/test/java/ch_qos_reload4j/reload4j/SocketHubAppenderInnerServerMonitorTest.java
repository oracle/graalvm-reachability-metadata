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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketHubAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class SocketHubAppenderInnerServerMonitorTest {
    private static final Logger LOGGER = Logger.getLogger(SocketHubAppenderInnerServerMonitorTest.class);
    private static final String CACHED_MESSAGE = "cached socket hub event";
    private static final int TIMEOUT_MILLIS = 10_000;

    @Test
    void connectingClientReceivesEventsCachedByServerMonitor() throws Exception {
        LoopbackSocketHubAppender appender = new LoopbackSocketHubAppender();
        try {
            appender.setName("socket-hub-server-monitor-test");
            appender.setPort(0);
            appender.setBufferSize(1);
            appender.activateOptions();

            LoggingEvent cachedEvent = new LoggingEvent(SocketHubAppenderInnerServerMonitorTest.class.getName(), LOGGER,
                    Level.WARN, CACHED_MESSAGE, null);
            appender.append(cachedEvent);

            LoggingEvent restored = readFirstCachedEvent(appender.awaitPort());
            assertThat(restored.getLoggerName()).isEqualTo(LOGGER.getName());
            assertThat(restored.getLevel()).isEqualTo(Level.WARN);
            assertThat(restored.getMessage()).isEqualTo(CACHED_MESSAGE);
        } finally {
            appender.close();
        }
    }

    private static LoggingEvent readFirstCachedEvent(int port) throws Exception {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), TIMEOUT_MILLIS);
            try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                Object restored = input.readObject();
                assertThat(restored).isInstanceOf(LoggingEvent.class);
                return (LoggingEvent) restored;
            }
        }
    }

    private static final class LoopbackSocketHubAppender extends SocketHubAppender {
        private final CompletableFuture<Integer> port = new CompletableFuture<>();

        int awaitPort() throws Exception {
            return port.get(10, TimeUnit.SECONDS);
        }

        @Override
        protected ServerSocket createServerSocket(int socketPort) throws IOException {
            ServerSocket serverSocket = new ServerSocket(socketPort, 50, InetAddress.getLoopbackAddress());
            port.complete(serverSocket.getLocalPort());
            return serverSocket;
        }
    }
}
