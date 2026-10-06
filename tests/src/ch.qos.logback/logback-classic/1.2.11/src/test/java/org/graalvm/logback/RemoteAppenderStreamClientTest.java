/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ServerSocketFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.server.ServerSocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteAppenderStreamClientTest {

  private static final int TIMEOUT_MILLIS = 10_000;

  @Test
  void serverReceiverDispatchesAnEventThroughItsStreamClient() throws Exception {
    LoggerContext context = new LoggerContext();
    AddressCapturingReceiver receiver = new AddressCapturingReceiver();
    EventAppender appender = new EventAppender();
    try {
      Logger logger = context.getLogger("org.graalvm.logback.RemoteAppenderStreamClientTest.remote");
      logger.setAdditive(false);
      logger.setLevel(Level.INFO);
      appender.setContext(context);
      appender.start();
      logger.addAppender(appender);

      receiver.setContext(context);
      receiver.setAddress(InetAddress.getLoopbackAddress().getHostAddress());
      receiver.setPort(0);
      receiver.start();

      try (Socket socket = new Socket()) {
        socket.connect(receiver.getSocketAddress(), TIMEOUT_MILLIS);
        try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
          output.writeObject(eventFor(logger, "remote stream payload"));
          output.flush();
          assertThat(appender.awaitEvent().getFormattedMessage()).isEqualTo("remote stream payload");
        }
      }
    } finally {
      receiver.stop();
      appender.stop();
      context.stop();
    }
  }

  private static LoggingEventVO eventFor(Logger logger, String message) {
    LoggingEvent event = new LoggingEvent(RemoteAppenderStreamClientTest.class.getName(), logger, Level.INFO, message,
        null, null);
    return LoggingEventVO.build(event);
  }

  private static final class AddressCapturingReceiver extends ServerSocketReceiver {

    private final AddressCapturingServerSocketFactory socketFactory = new AddressCapturingServerSocketFactory();

    @Override
    protected ServerSocketFactory getServerSocketFactory() {
      return socketFactory;
    }

    SocketAddress getSocketAddress() {
      return socketFactory.getSocketAddress();
    }
  }

  private static final class AddressCapturingServerSocketFactory extends ServerSocketFactory {

    private final ServerSocketFactory delegate = ServerSocketFactory.getDefault();
    private final AtomicReference<ServerSocket> socket = new AtomicReference<>();

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
      return capture(delegate.createServerSocket(port));
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog) throws IOException {
      return capture(delegate.createServerSocket(port, backlog));
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress address) throws IOException {
      return capture(delegate.createServerSocket(port, backlog, address));
    }

    SocketAddress getSocketAddress() {
      ServerSocket value = socket.get();
      assertThat(value).isNotNull();
      return value.getLocalSocketAddress();
    }

    private ServerSocket capture(ServerSocket value) {
      socket.set(value);
      return value;
    }
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
