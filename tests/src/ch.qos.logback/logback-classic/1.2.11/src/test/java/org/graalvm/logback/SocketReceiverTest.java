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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketReceiverTest {

  private static final int TIMEOUT_MILLIS = 10_000;

  @Test
  void receivesAnEventFromARemoteAppenderConnection() throws Exception {
    LoggerContext context = new LoggerContext();
    SocketReceiver receiver = new SocketReceiver();
    EventAppender appender = new EventAppender();
    try (ServerSocket sender = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      sender.setSoTimeout(TIMEOUT_MILLIS);
      Logger logger = context.getLogger("org.graalvm.logback.SocketReceiverTest.remote");
      logger.setAdditive(false);
      logger.setLevel(Level.INFO);
      appender.setContext(context);
      appender.start();
      logger.addAppender(appender);

      receiver.setContext(context);
      receiver.setRemoteHost(InetAddress.getLoopbackAddress().getHostAddress());
      receiver.setPort(sender.getLocalPort());
      receiver.setReconnectionDelay(TIMEOUT_MILLIS);
      receiver.setAcceptConnectionTimeout(TIMEOUT_MILLIS);
      receiver.start();

      try (Socket socket = sender.accept();
          ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
        output.writeObject(eventFor(logger, "socket receiver payload"));
        output.flush();

        assertThat(appender.awaitEvent().getFormattedMessage()).isEqualTo("socket receiver payload");
      }
    } finally {
      receiver.stop();
      appender.stop();
      context.stop();
    }
  }

  private static LoggingEventVO eventFor(Logger logger, String message) {
    LoggingEvent event = new LoggingEvent(SocketReceiverTest.class.getName(), logger, Level.INFO, message, null, null);
    return LoggingEventVO.build(event);
  }

  private static final class EventAppender extends AppenderBase<ILoggingEvent> {

    private final CountDownLatch received = new CountDownLatch(1);
    private final AtomicReference<ILoggingEvent> event = new AtomicReference<>();

    @Override
    protected void append(ILoggingEvent value) {
      event.set(value);
      received.countDown();
    }

    ILoggingEvent awaitEvent() throws InterruptedException {
      assertThat(received.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
      return event.get();
    }
  }
}
