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
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketNodeTest {

  private static final int TIMEOUT_MILLIS = 10_000;

  @Test
  void forwardsAnEventReadFromItsSocket() throws Exception {
    LoggerContext context = new LoggerContext();
    try {
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.setContext(context);
      appender.start();
      Logger logger = context.getLogger("org.graalvm.logback.SocketNodeTest.remote");
      logger.setAdditive(false);
      logger.setLevel(Level.INFO);
      logger.addAppender(appender);

      try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
          Socket writer = new Socket()) {
        listener.setSoTimeout(TIMEOUT_MILLIS);
        writer.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()),
            TIMEOUT_MILLIS);
        try (Socket reader = listener.accept()) {
          reader.setSoTimeout(TIMEOUT_MILLIS);
          SocketNode node = new SocketNode(new SimpleSocketServer(context, listener.getLocalPort()), reader, context);
          Thread nodeThread = new Thread(node, "logback-socket-node-test");
          nodeThread.setDaemon(true);
          nodeThread.start();

          try (ObjectOutputStream output = new ObjectOutputStream(writer.getOutputStream())) {
            output.writeObject(eventFor(logger, "socket node payload"));
            output.flush();
          }

          nodeThread.join(TIMEOUT_MILLIS);
          assertThat(nodeThread.isAlive()).isFalse();
          assertThat(appender.list).singleElement()
              .extracting(ILoggingEvent::getFormattedMessage)
              .isEqualTo("socket node payload");
        }
      }
    } finally {
      context.stop();
    }
  }

  private static LoggingEventVO eventFor(Logger logger, String message) {
    LoggingEvent event = new LoggingEvent(SocketNodeTest.class.getName(), logger, Level.INFO, message, null, null);
    return LoggingEventVO.build(event);
  }
}
