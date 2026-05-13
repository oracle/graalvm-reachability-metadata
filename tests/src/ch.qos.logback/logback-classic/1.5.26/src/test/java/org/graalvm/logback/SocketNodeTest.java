/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.BufferedOutputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SimpleSocketServer;
import ch.qos.logback.classic.net.SocketNode;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketNodeTest {

  @Test
  void shouldReadSerializedLoggingEventFromSocketAndDispatchToContextLogger() throws Exception {
    LoggerContext context = new LoggerContext();
    context.setName("socket-node-test");
    context.setMDCAdapter(new LogbackMDCAdapter());
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Socket clientSocket = new Socket(serverSocket.getInetAddress(), serverSocket.getLocalPort());
        Socket serverSideSocket = serverSocket.accept()) {
      ListAppender<ILoggingEvent> appender = createListAppender(context);
      Logger logger = createLogger(context, appender);
      SimpleSocketServer socketServer = new SimpleSocketServer(context, serverSocket.getLocalPort());

      try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(
          new BufferedOutputStream(clientSocket.getOutputStream()))) {
        objectOutputStream.flush();
        Future<?> socketNodeRun = executorService.submit(new SocketNode(socketServer, serverSideSocket, context));

        objectOutputStream.writeObject(createEventVO(logger));
        objectOutputStream.flush();
        clientSocket.shutdownOutput();
        socketNodeRun.get(5, TimeUnit.SECONDS);
      }

      assertThat(appender.list).singleElement()
          .satisfies(event -> {
            assertThat(event.getLoggerName()).isEqualTo(logger.getName());
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("socket-node message");
          });
    } finally {
      executorService.shutdownNow();
      context.stop();
    }
  }

  private ListAppender<ILoggingEvent> createListAppender(LoggerContext context) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(context);
    appender.start();
    return appender;
  }

  private Logger createLogger(LoggerContext context, ListAppender<ILoggingEvent> appender) {
    Logger logger = context.getLogger("org.graalvm.logback.SocketNodeTest.remote");
    logger.setLevel(Level.DEBUG);
    logger.setAdditive(false);
    logger.addAppender(appender);
    return logger;
  }

  private LoggingEventVO createEventVO(Logger logger) {
    LoggingEvent event = new LoggingEvent(SocketNodeTest.class.getName(), logger, Level.INFO, "socket-node message",
        null, null);
    return LoggingEventVO.build(event);
  }
}
