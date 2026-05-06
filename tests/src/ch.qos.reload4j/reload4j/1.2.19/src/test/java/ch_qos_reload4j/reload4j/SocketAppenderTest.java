/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketAppenderTest {
    private static final String LOGGER_NAME = "reload4j.socket-appender";

    @Test
    void sendsSerializedLoggingEventToSocketServer() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        CountDownLatch appenderFinished = new CountDownLatch(1);
        SocketAppender appender = null;
        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            Future<LoggingEvent> receivedEvent = executorService.submit(
                    () -> readSingleLoggingEvent(serverSocket, appenderFinished));
            appender = new SocketAppender(loopbackAddress, serverSocket.getLocalPort());
            appender.setApplication("socket-appender-test-application");
            appender.setLocationInfo(true);

            LoggingEvent sentEvent = new LoggingEvent(
                    SocketAppenderTest.class.getName(),
                    Logger.getLogger(LOGGER_NAME),
                    Level.ERROR,
                    "message sent through SocketAppender",
                    new IllegalStateException("socket appender test throwable"));

            appender.append(sentEvent);
            appenderFinished.countDown();

            LoggingEvent deserializedEvent = receivedEvent.get(5, TimeUnit.SECONDS);
            assertThat(deserializedEvent.getLoggerName()).isEqualTo(LOGGER_NAME);
            assertThat(deserializedEvent.getLevel()).isSameAs(Level.ERROR);
            assertThat(deserializedEvent.getRenderedMessage()).isEqualTo("message sent through SocketAppender");
            assertThat(deserializedEvent.getProperty("application")).isEqualTo("socket-appender-test-application");
            assertThat(deserializedEvent.getLocationInformation()).isNotNull();
            assertThat(deserializedEvent.getThrowableStrRep()).isNotEmpty();
        } finally {
            appenderFinished.countDown();
            if (appender != null) {
                appender.close();
            }
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static LoggingEvent readSingleLoggingEvent(
            ServerSocket serverSocket,
            CountDownLatch appenderFinished) throws Exception {
        try (Socket socket = serverSocket.accept();
                ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
            Object receivedObject = inputStream.readObject();
            assertThat(appenderFinished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedObject).isInstanceOf(LoggingEvent.class);
            return (LoggingEvent) receivedObject;
        }
    }
}
