/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SocketAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketReceiverTest {

  private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

  @Test
  void socketAppenderSendsSerializedLoggingEventToRemoteReceiver() throws Exception {
    LoggerContext loggerContext = new LoggerContext();
    loggerContext.setName("socket-receiver-test");
    loggerContext.setMDCAdapter(new LogbackMDCAdapter());

    SocketAppender appender = new SocketAppender();
    appender.setContext(loggerContext);
    appender.setName("socket-receiver-test-appender");
    appender.setRemoteHost(InetAddress.getLoopbackAddress().getHostAddress());
    appender.setIncludeCallerData(true);

    try (ServerSocket receiverServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      receiverServer.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
      appender.setPort(receiverServer.getLocalPort());
      appender.start();

      assertThat(appender.isStarted()).isTrue();

      Logger remoteLogger = loggerContext.getLogger("org.graalvm.logback.SocketReceiverTest.remote");
      remoteLogger.setAdditive(false);
      remoteLogger.setLevel(Level.INFO);
      remoteLogger.addAppender(appender);

      try (Socket receiverSocket = receiverServer.accept()) {
        receiverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        try (ObjectInputStream inputStream = new ObjectInputStream(receiverSocket.getInputStream())) {
          appender.doAppend(createLoggingEvent(remoteLogger, "message from socket appender"));

          Object serializedEvent = inputStream.readObject();
          assertThat(serializedEvent).isInstanceOf(ILoggingEvent.class);
          ILoggingEvent receivedEvent = (ILoggingEvent) serializedEvent;
          assertThat(receivedEvent.getFormattedMessage()).isEqualTo("message from socket appender");
          assertThat(receivedEvent.hasCallerData()).isTrue();
        }
      }
    } finally {
      appender.stop();
      loggerContext.stop();
    }
  }

  private static LoggingEvent createLoggingEvent(Logger logger, String message) {
    return new LoggingEvent(SocketReceiverTest.class.getName(), logger, Level.INFO, message, null, null) {
      @Override
      public LoggerContextVO getLoggerContextVO() {
        return null;
      }
    };
  }
}
