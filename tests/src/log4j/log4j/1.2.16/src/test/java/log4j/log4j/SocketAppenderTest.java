/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Hierarchy;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.RootLogger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketAppenderTest {

    @Test
    void sendsSerializedLoggingEventsToTheRemoteSocket() throws Exception {
        String loggerName = SocketAppenderTest.class.getName() + "." + System.nanoTime();
        Hierarchy hierarchy = new Hierarchy(new RootLogger(Level.DEBUG));
        Logger logger = hierarchy.getLogger(loggerName);
        logger.setAdditivity(false);
        logger.setLevel(Level.DEBUG);

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            serverSocket.setSoTimeout(5000);

            SocketAppender appender = new SocketAppender(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
            appender.setLocationInfo(true);
            appender.setApplication("socket-appender-test");
            logger.addAppender(appender);

            try (Socket serverSideSocket = serverSocket.accept()) {
                serverSideSocket.setSoTimeout(5000);

                IllegalStateException failure = new IllegalStateException("boom");
                emitLogEvent(logger, "remote message", failure);

                try (ObjectInputStream objectInput = new ObjectInputStream(serverSideSocket.getInputStream())) {
                    LoggingEvent event = (LoggingEvent) objectInput.readObject();

                    assertThat(event.getLoggerName()).isEqualTo(loggerName);
                    assertThat(event.getRenderedMessage()).isEqualTo("remote message");
                    assertThat(event.getProperty("application")).isEqualTo("socket-appender-test");
                    assertThat(event.getThrowableStrRep()).isNotEmpty();
                    assertThat(event.getThrowableStrRep()[0]).contains("IllegalStateException: boom");
                    assertThat(event.getLocationInformation().getClassName()).isEqualTo(SocketAppenderTest.class.getName());
                    assertThat(event.getLocationInformation().getMethodName()).isEqualTo("emitLogEvent");
                }
            } finally {
                logger.removeAppender(appender);
                appender.close();
            }
        }
    }

    private static void emitLogEvent(Logger logger, String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
