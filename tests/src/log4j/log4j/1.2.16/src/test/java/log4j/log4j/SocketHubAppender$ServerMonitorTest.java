/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Hierarchy;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketHubAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.RootLogger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocketHubAppenderServerMonitorTest {

    @Test
    void sendsBufferedEventsToNewlyConnectedClients() throws Exception {
        String loggerName = SocketHubAppenderServerMonitorTest.class.getName() + "." + System.nanoTime();
        Hierarchy hierarchy = new Hierarchy(new RootLogger(Level.DEBUG));
        Logger logger = hierarchy.getLogger(loggerName);
        logger.setAdditivity(false);
        logger.setLevel(Level.DEBUG);

        TestableSocketHubAppender appender = new TestableSocketHubAppender();
        appender.setPort(0);
        appender.setBufferSize(4);
        appender.activateOptions();
        logger.addAppender(appender);

        try {
            logger.warn("buffered message");

            try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), appender.awaitLocalPort())) {
                clientSocket.setSoTimeout(5000);

                try (ObjectInputStream objectInput = new ObjectInputStream(clientSocket.getInputStream())) {
                    LoggingEvent event = (LoggingEvent) objectInput.readObject();
                    assertThat(event.getLoggerName()).isEqualTo(loggerName);
                    assertThat(event.getRenderedMessage()).isEqualTo("buffered message");
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                }
            }
        } finally {
            logger.removeAppender(appender);
            appender.close();
        }
    }

    private static final class TestableSocketHubAppender extends SocketHubAppender {
        private final CountDownLatch serverReady = new CountDownLatch(1);
        private volatile int localPort;

        @Override
        protected ServerSocket createServerSocket(int socketPort) throws IOException {
            ServerSocket serverSocket = new ServerSocket(socketPort, 1, InetAddress.getLoopbackAddress());
            localPort = serverSocket.getLocalPort();
            serverReady.countDown();
            return serverSocket;
        }

        private int awaitLocalPort() throws InterruptedException {
            assertThat(serverReady.await(5, TimeUnit.SECONDS)).isTrue();
            return localPort;
        }
    }
}
