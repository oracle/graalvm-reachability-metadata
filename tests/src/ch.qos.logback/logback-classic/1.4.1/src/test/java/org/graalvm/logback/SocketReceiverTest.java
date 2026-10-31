/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import org.graalvm.logback.util.EventQueueAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketReceiverTest {

  private static final int SOCKET_TIMEOUT_MILLIS = 10_000;

  @Test
  void dispatchesSerializedLoggingEventFromRemoteSocket() throws Exception {
    LoggerContext loggerContext = new LoggerContext();
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) loggerContext.getScheduledExecutorService();
    // The receiver and its connector need separate workers.
    executor.setCorePoolSize(2);
    SocketReceiver receiver = new SocketReceiver();
    EventQueueAppender appender = new EventQueueAppender();
    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
      Logger remoteLogger = loggerContext.getLogger("org.graalvm.logback.SocketReceiverTest.remote");
      remoteLogger.setAdditive(false);
      remoteLogger.setLevel(Level.INFO);
      appender.setContext(loggerContext);
      appender.start();
      remoteLogger.addAppender(appender);

      receiver.setContext(loggerContext);
      receiver.setRemoteHost(InetAddress.getLoopbackAddress().getHostAddress());
      receiver.setPort(serverSocket.getLocalPort());
      receiver.setReconnectionDelay(SOCKET_TIMEOUT_MILLIS);
      receiver.setAcceptConnectionTimeout(SOCKET_TIMEOUT_MILLIS);
      receiver.start();
      assertThat(receiver.isStarted()).isTrue();

      try (Socket socket = serverSocket.accept();
          ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
        LoggingEvent event = new LoggingEvent(SocketReceiverTest.class.getName(), remoteLogger, Level.WARN,
            "received {}", null, new Object[] {"remotely"});
        outputStream.writeObject(LoggingEventVO.build(event));
        outputStream.flush();

        ILoggingEvent receivedEvent = appender.awaitEvent();
        assertThat(receivedEvent.getLoggerName()).isEqualTo(remoteLogger.getName());
        assertThat(receivedEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(receivedEvent.getMessage()).isEqualTo("received {}");
        assertThat(receivedEvent.getFormattedMessage()).isEqualTo("received remotely");
      }
    } finally {
      receiver.stop();
      appender.stop();
      loggerContext.stop();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }
}
