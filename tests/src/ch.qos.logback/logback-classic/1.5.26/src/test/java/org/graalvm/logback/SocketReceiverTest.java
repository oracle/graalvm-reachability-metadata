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
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketReceiverTest {

  @Test
  void shouldReceiveSerializedLoggingEventFromRemoteServerAndDispatchToContextLogger() throws Exception {
    LoggerContext context = new LoggerContext();
    context.setName("socket-receiver-test");
    context.setMDCAdapter(new LogbackMDCAdapter());
    ExecutorService serverExecutor = Executors.newSingleThreadExecutor();

    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ListAppender<ILoggingEvent> appender = createListAppender(context);
      Logger logger = createLogger(context, appender);
      LoggingEventVO remoteEvent = createEventVO(logger);
      Future<?> serverFuture = serverExecutor.submit(() -> {
        writeEvent(serverSocket, remoteEvent);
        return null;
      });

      SocketReceiver receiver = createReceiver(context, serverSocket.getInetAddress(), serverSocket.getLocalPort());
      try {
        receiver.start();

        waitForEvent(appender, Duration.ofSeconds(5));
        serverFuture.get(5, TimeUnit.SECONDS);
      } finally {
        receiver.stop();
      }

      assertThat(appender.list).singleElement()
          .satisfies(event -> {
            assertThat(event.getLoggerName()).isEqualTo(logger.getName());
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("socket-receiver message");
          });
    } finally {
      context.stop();
      serverExecutor.shutdownNow();
      serverExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private void writeEvent(ServerSocket serverSocket, LoggingEventVO remoteEvent) throws Exception {
    try (Socket socket = serverSocket.accept();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream())) {
      objectOutputStream.writeObject(remoteEvent);
      objectOutputStream.flush();
      socket.shutdownOutput();
    }
  }

  private SocketReceiver createReceiver(LoggerContext context, InetAddress remoteAddress, int remotePort) {
    SocketReceiver receiver = new SocketReceiver();
    receiver.setContext(context);
    receiver.setRemoteHost(remoteAddress.getHostAddress());
    receiver.setPort(remotePort);
    receiver.setReconnectionDelay(100);
    receiver.setAcceptConnectionTimeout(2_000);
    return receiver;
  }

  private ListAppender<ILoggingEvent> createListAppender(LoggerContext context) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(context);
    appender.start();
    return appender;
  }

  private Logger createLogger(LoggerContext context, ListAppender<ILoggingEvent> appender) {
    Logger logger = context.getLogger("org.graalvm.logback.SocketReceiverTest.remote");
    logger.setLevel(Level.DEBUG);
    logger.setAdditive(false);
    logger.addAppender(appender);
    return logger;
  }

  private LoggingEventVO createEventVO(Logger logger) {
    LoggingEvent event = new LoggingEvent(SocketReceiverTest.class.getName(), logger, Level.INFO,
        "socket-receiver message", null, null);
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
