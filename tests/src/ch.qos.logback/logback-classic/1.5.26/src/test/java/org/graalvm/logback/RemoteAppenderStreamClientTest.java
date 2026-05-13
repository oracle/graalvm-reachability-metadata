/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.server.ServerSocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteAppenderStreamClientTest {

  @Test
  void shouldReadSerializedLoggingEventFromRemoteAppenderClientAndDispatchToContextLogger() throws Exception {
    LoggerContext context = new LoggerContext();
    context.setName("remote-appender-stream-client-test");
    context.setMDCAdapter(new LogbackMDCAdapter());
    ServerSocketReceiver receiver = null;

    try {
      ListAppender<ILoggingEvent> appender = createListAppender(context);
      Logger logger = createLogger(context, appender);
      int port = findAvailablePort();
      receiver = createReceiver(context, port);
      receiver.start();

      assertThat(receiver.isStarted()).isTrue();
      writeEvent(port, createEventVO(logger));
      waitForEvent(appender, Duration.ofSeconds(5));

      assertThat(appender.list).singleElement()
          .satisfies(event -> {
            assertThat(event.getLoggerName()).isEqualTo(logger.getName());
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("remote-appender-stream-client message");
          });
    } finally {
      if (receiver != null && receiver.isStarted()) {
        receiver.stop();
      }
      context.stop();
    }
  }

  private int findAvailablePort() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return serverSocket.getLocalPort();
    }
  }

  private ServerSocketReceiver createReceiver(LoggerContext context, int port) {
    ServerSocketReceiver receiver = new ServerSocketReceiver();
    receiver.setContext(context);
    receiver.setAddress(InetAddress.getLoopbackAddress().getHostAddress());
    receiver.setPort(port);
    receiver.setBacklog(1);
    return receiver;
  }

  private void writeEvent(int port, LoggingEventVO remoteEvent) throws IOException {
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(
            new BufferedOutputStream(socket.getOutputStream()))) {
      objectOutputStream.flush();
      objectOutputStream.writeObject(remoteEvent);
      objectOutputStream.flush();
      socket.shutdownOutput();
    }
  }

  private ListAppender<ILoggingEvent> createListAppender(LoggerContext context) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(context);
    appender.start();
    return appender;
  }

  private Logger createLogger(LoggerContext context, ListAppender<ILoggingEvent> appender) {
    Logger logger = context.getLogger("org.graalvm.logback.RemoteAppenderStreamClientTest.remote");
    logger.setLevel(Level.DEBUG);
    logger.setAdditive(false);
    logger.addAppender(appender);
    return logger;
  }

  private LoggingEventVO createEventVO(Logger logger) {
    LoggingEvent event = new LoggingEvent(RemoteAppenderStreamClientTest.class.getName(), logger, Level.INFO,
        "remote-appender-stream-client message", null, null);
    return LoggingEventVO.build(event);
  }

  private void waitForEvent(ListAppender<ILoggingEvent> appender, Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (appender.list.isEmpty() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(appender.list).isNotEmpty();
  }
}
