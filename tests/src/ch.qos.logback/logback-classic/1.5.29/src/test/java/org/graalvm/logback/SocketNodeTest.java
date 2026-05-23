/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SimpleSocketServer;
import ch.qos.logback.classic.net.SocketNode;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketNodeTest {

  private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

  @Test
  void readsSerializedLoggingEventFromSocket() throws Exception {
    LoggerContext loggerContext = new LoggerContext();
    loggerContext.setName("socket-node-test");
    loggerContext.setMDCAdapter(new LogbackMDCAdapter());

    try {
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.setContext(loggerContext);
      appender.start();

      Logger remoteLogger = loggerContext.getLogger("org.graalvm.logback.SocketNodeTest.remote");
      remoteLogger.setAdditive(false);
      remoteLogger.setLevel(Level.INFO);
      remoteLogger.addAppender(appender);

      try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
          Socket clientSocket = new Socket()) {
        serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        clientSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()),
            SOCKET_TIMEOUT_MILLIS);

        try (Socket acceptedSocket = serverSocket.accept()) {
          SimpleSocketServer socketServer = new SimpleSocketServer(loggerContext, serverSocket.getLocalPort());
          SocketNode socketNode = new SocketNode(socketServer, acceptedSocket, loggerContext);
          Thread socketNodeThread = new Thread(socketNode, "socket-node-test-reader");
          socketNodeThread.setDaemon(true);
          socketNodeThread.start();

          LoggingEventVO loggingEvent = createLoggingEvent(remoteLogger, "message from a remote socket");
          try (ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream())) {
            outputStream.writeObject(loggingEvent);
            outputStream.flush();
          }

          socketNodeThread.join(SOCKET_TIMEOUT_MILLIS);
          assertThat(socketNodeThread.isAlive()).isFalse();
          assertThat(appender.list).hasSize(1);
          assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("message from a remote socket");
        }
      }
    } finally {
      loggerContext.stop();
    }
  }

  private static LoggingEventVO createLoggingEvent(Logger logger, String message) {
    LoggingEvent loggingEvent = new LoggingEvent(SocketNodeTest.class.getName(), logger, Level.INFO, message, null,
        null) {
      @Override
      public LoggerContextVO getLoggerContextVO() {
        return null;
      }
    };
    return LoggingEventVO.build(loggingEvent);
  }
}
