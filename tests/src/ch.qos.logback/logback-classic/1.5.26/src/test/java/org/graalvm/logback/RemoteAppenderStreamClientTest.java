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
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteAppenderStreamClientTest {

  private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

  @Test
  void serverSocketReceiverDispatchesSerializedRemoteEvent() throws Exception {
    LoggerContext loggerContext = new LoggerContext();
    loggerContext.setName("remote-appender-stream-client-test");
    loggerContext.setMDCAdapter(new LogbackMDCAdapter());

    CapturingAppender appender = new CapturingAppender();
    CapturingServerSocketReceiver receiver = new CapturingServerSocketReceiver();
    try {
      Logger remoteLogger = loggerContext.getLogger("org.graalvm.logback.RemoteAppenderStreamClientTest.remote");
      remoteLogger.setAdditive(false);
      remoteLogger.setLevel(Level.INFO);
      appender.setContext(loggerContext);
      appender.start();
      remoteLogger.addAppender(appender);

      receiver.setContext(loggerContext);
      receiver.setAddress(InetAddress.getLoopbackAddress().getHostAddress());
      receiver.setPort(0);
      receiver.start();

      try (Socket socket = new Socket()) {
        socket.connect(receiver.getSocketAddress(), SOCKET_TIMEOUT_MILLIS);
        try (ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
          outputStream.writeObject(createLoggingEvent(remoteLogger, "message from remote appender stream client"));
          outputStream.flush();
        }
      }

      ILoggingEvent receivedEvent = appender.awaitEvent();
      assertThat(receivedEvent.getFormattedMessage()).isEqualTo("message from remote appender stream client");
    } finally {
      receiver.stop();
      appender.stop();
      loggerContext.stop();
    }
  }

  private static LoggingEventVO createLoggingEvent(Logger logger, String message) {
    LoggingEvent loggingEvent = new LoggingEvent(RemoteAppenderStreamClientTest.class.getName(), logger, Level.INFO,
        message, null, null);
    return LoggingEventVO.build(loggingEvent);
  }

  private static final class CapturingServerSocketReceiver extends ServerSocketReceiver {

    private final CapturingServerSocketFactory serverSocketFactory = new CapturingServerSocketFactory();

    @Override
    protected ServerSocketFactory getServerSocketFactory() {
      return serverSocketFactory;
    }

    SocketAddress getSocketAddress() {
      return serverSocketFactory.getSocketAddress();
    }
  }

  private static final class CapturingServerSocketFactory extends ServerSocketFactory {

    private final ServerSocketFactory delegate = ServerSocketFactory.getDefault();
    private final AtomicReference<ServerSocket> serverSocketReference = new AtomicReference<>();

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
      return capture(delegate.createServerSocket(port));
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog) throws IOException {
      return capture(delegate.createServerSocket(port, backlog));
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
      return capture(delegate.createServerSocket(port, backlog, ifAddress));
    }

    SocketAddress getSocketAddress() {
      ServerSocket serverSocket = serverSocketReference.get();
      assertThat(serverSocket).isNotNull();
      return serverSocket.getLocalSocketAddress();
    }

    private ServerSocket capture(ServerSocket serverSocket) {
      serverSocketReference.set(serverSocket);
      return serverSocket;
    }
  }

  private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

    private final CountDownLatch eventReceived = new CountDownLatch(1);
    private final AtomicReference<ILoggingEvent> eventReference = new AtomicReference<>();

    @Override
    protected void append(ILoggingEvent eventObject) {
      eventReference.set(eventObject);
      eventReceived.countDown();
    }

    ILoggingEvent awaitEvent() throws InterruptedException {
      assertThat(eventReceived.await(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
      return eventReference.get();
    }
  }
}
