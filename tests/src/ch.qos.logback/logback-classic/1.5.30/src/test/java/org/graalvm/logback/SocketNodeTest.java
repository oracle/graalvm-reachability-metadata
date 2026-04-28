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
  void readsSerializedLoggingEventsFromSocket() throws Exception {
    LoggerContext context = createLoggerContext();
    Logger remoteLogger = context.getLogger("socket-node-test.remote");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(context);
    appender.start();
    remoteLogger.addAppender(appender);

    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
         Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
         Socket serverSideSocket = serverSocket.accept();
         ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream())) {
      SimpleSocketServer socketServer = new SimpleSocketServer(context, serverSocket.getLocalPort());
      SocketNode socketNode = new SocketNode(socketServer, serverSideSocket, context);
      Thread socketNodeThread = new Thread(socketNode, "logback-socket-node-test");

      socketNodeThread.start();
      outputStream.writeObject(createLoggingEvent(remoteLogger));
      outputStream.flush();
      clientSocket.shutdownOutput();

      socketNodeThread.join(5_000L);
      if (socketNodeThread.isAlive()) {
        serverSideSocket.close();
        socketNodeThread.join(5_000L);
      }

      assertThat(socketNodeThread.isAlive()).isFalse();
      assertThat(appender.list)
          .hasSize(1)
          .first()
          .satisfies(event -> {
            assertThat(event.getLoggerName()).isEqualTo(remoteLogger.getName());
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("socket node message");
          });
    } finally {
      remoteLogger.detachAppender(appender);
      context.stop();
    }
  }

  private static LoggerContext createLoggerContext() {
    LoggerContext context = new LoggerContext();
    context.setName("socket-node-test");
    context.setMDCAdapter(new LogbackMDCAdapter());
    return context;
  }

  private static LoggingEventVO createLoggingEvent(Logger logger) {
    LoggingEvent event = new LoggingEvent(
        SocketNodeTest.class.getName(), logger, Level.INFO, "socket node message", null, null);
    event.prepareForDeferredProcessing();
    return LoggingEventVO.build(event);
  }
}
