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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketHubAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketHubAppenderTest {
    private static final String LOGGER_NAME = "reload4j.socket-hub-appender";

    @Test
    void sendsSerializedLoggingEventToAcceptedSocketClient() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        TestSocketHubAppender appender = new TestSocketHubAppender();
        try {
            appender.setName("socket-hub-appender-test");
            appender.setApplication("socket-hub-appender-test-application");
            appender.setLocationInfo(true);
            appender.setPort(0);
            appender.activateOptions();

            Future<LoggingEvent> receivedEvent = executorService.submit(
                    () -> readSingleLoggingEvent(appender.awaitPort()));
            LoggingEvent sentEvent = new LoggingEvent(
                    SocketHubAppenderTest.class.getName(),
                    Logger.getLogger(LOGGER_NAME),
                    Level.WARN,
                    "message sent through SocketHubAppender",
                    new IllegalArgumentException("socket hub appender test throwable"));

            LoggingEvent deserializedEvent = appendUntilReceived(appender, sentEvent, receivedEvent);

            assertThat(deserializedEvent.getLoggerName()).isEqualTo(LOGGER_NAME);
            assertThat(deserializedEvent.getLevel()).isSameAs(Level.WARN);
            assertThat(deserializedEvent.getRenderedMessage()).isEqualTo("message sent through SocketHubAppender");
            assertThat(deserializedEvent.getProperty("application")).isEqualTo("socket-hub-appender-test-application");
            assertThat(deserializedEvent.getLocationInformation()).isNotNull();
            assertThat(deserializedEvent.getThrowableStrRep()).isNotEmpty();
        } finally {
            appender.close();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static LoggingEvent appendUntilReceived(
            SocketHubAppender appender,
            LoggingEvent sentEvent,
            Future<LoggingEvent> receivedEvent) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            appender.append(sentEvent);
            try {
                return receivedEvent.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeoutException) {
                Thread.sleep(10);
            }
        }
        return receivedEvent.get(1, TimeUnit.SECONDS);
    }

    private static LoggingEvent readSingleLoggingEvent(int port) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
            Object receivedObject = inputStream.readObject();
            assertThat(receivedObject).isInstanceOf(LoggingEvent.class);
            return (LoggingEvent) receivedObject;
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
