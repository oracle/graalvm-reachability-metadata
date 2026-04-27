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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Hierarchy;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketHubAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.RootLogger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketHubAppenderTest {

    @Test
    void sendsSerializedLoggingEventsToConnectedClients() throws Exception {
        String loggerName = SocketHubAppenderTest.class.getName() + "." + System.nanoTime();
        Hierarchy hierarchy = new Hierarchy(new RootLogger(Level.DEBUG));
        Logger logger = hierarchy.getLogger(loggerName);
        logger.setAdditivity(false);
        logger.setLevel(Level.DEBUG);

        TestableSocketHubAppender appender = new TestableSocketHubAppender();
        appender.setPort(0);
        appender.setLocationInfo(true);
        appender.setApplication("socket-hub-appender-test");
        appender.activateOptions();
        logger.addAppender(appender);

        try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), appender.awaitLocalPort())) {
            clientSocket.setSoTimeout(5000);

            try (ObjectInputStream objectInput = new ObjectInputStream(clientSocket.getInputStream())) {
                AtomicReference<LoggingEvent> receivedEvent = new AtomicReference<>();
                AtomicReference<Throwable> readerFailure = new AtomicReference<>();
                CountDownLatch eventReceived = new CountDownLatch(1);
                Thread reader = new Thread(() -> {
                    try {
                        receivedEvent.set((LoggingEvent) objectInput.readObject());
                    } catch (Throwable throwable) {
                        readerFailure.set(throwable);
                    } finally {
                        eventReceived.countDown();
                    }
                }, "socket-hub-appender-reader");
                reader.start();

                for (int attempt = 0; attempt < 10; attempt++) {
                    emitLogEvent(logger, "live message", new IllegalArgumentException("boom"));
                    if (eventReceived.await(100, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                }

                assertThat(eventReceived.await(5, TimeUnit.SECONDS)).isTrue();
                reader.join(5000);
                assertThat(reader.isAlive()).isFalse();
                assertThat(readerFailure.get()).isNull();

                LoggingEvent event = receivedEvent.get();
                assertThat(event).isNotNull();
                assertThat(event.getLoggerName()).isEqualTo(loggerName);
                assertThat(event.getRenderedMessage()).isEqualTo("live message");
                assertThat(event.getProperty("application")).isEqualTo("socket-hub-appender-test");
                assertThat(event.getThrowableStrRep()).isNotEmpty();
                assertThat(event.getThrowableStrRep()[0]).contains("IllegalArgumentException: boom");
                assertThat(event.getLocationInformation().getClassName()).isEqualTo(SocketHubAppenderTest.class.getName());
                assertThat(event.getLocationInformation().getMethodName()).isEqualTo("emitLogEvent");
            }
        } finally {
            logger.removeAppender(appender);
            appender.close();
        }
    }

    private static void emitLogEvent(Logger logger, String message, Throwable throwable) {
        logger.error(message, throwable);
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
