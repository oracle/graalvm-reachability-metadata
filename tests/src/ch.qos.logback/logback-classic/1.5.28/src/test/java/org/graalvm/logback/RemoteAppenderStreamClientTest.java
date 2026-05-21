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
import java.net.InetSocketAddress;
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
import ch.qos.logback.classic.net.SimpleSocketServer;
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
  void simpleSocketServerDispatchesSerializedRemoteEvent() throws Exception {
    LoggerContext loggerContext = new LoggerContext();
    loggerContext.setName("remote-appender-stream-client-test");
    loggerContext.setMDCAdapter(new LogbackMDCAdapter());

    CapturingAppender appender = new CapturingAppender();
    CapturingSimpleSocketServer server = new CapturingSimpleSocketServer(loggerContext);
    try {
      Logger remoteLogger = loggerContext.getLogger("org.graalvm.logback.RemoteAppenderStreamClientTest.remote");
      remoteLogger.setAdditive(false);
      remoteLogger.setLevel(Level.INFO);
      appender.setContext(loggerContext);
      appender.start();
      remoteLogger.addAppender(appender);

      server.setDaemon(true);
      server.start();

      try (Socket socket = new Socket()) {
        socket.connect(server.awaitSocketAddress(), SOCKET_TIMEOUT_MILLIS);
        try (ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
          outputStream.writeObject(createLoggingEvent(remoteLogger, "message from simple socket server"));
          outputStream.flush();
        }
      }

      ILoggingEvent receivedEvent = appender.awaitEvent();
      assertThat(receivedEvent.getFormattedMessage()).isEqualTo("message from simple socket server");
    } finally {
      server.close();
      server.join(SOCKET_TIMEOUT_MILLIS);
      appender.stop();
      loggerContext.stop();
    }
  }

  private static LoggingEventVO createLoggingEvent(Logger logger, String message) {
    LoggingEvent loggingEvent = new LoggingEvent(RemoteAppenderStreamClientTest.class.getName(), logger, Level.INFO,
        message, null, null);
    return LoggingEventVO.build(loggingEvent);
  }

  private static final class CapturingSimpleSocketServer extends SimpleSocketServer {

    private final CapturingServerSocketFactory serverSocketFactory = new CapturingServerSocketFactory();

    CapturingSimpleSocketServer(LoggerContext loggerContext) {
      super(loggerContext, 0);
    }

    @Override
    protected ServerSocketFactory getServerSocketFactory() {
      return serverSocketFactory;
    }

    SocketAddress awaitSocketAddress() throws InterruptedException {
      return serverSocketFactory.awaitSocketAddress();
    }
  }

  private static final class CapturingServerSocketFactory extends ServerSocketFactory {

    private final ServerSocketFactory delegate = ServerSocketFactory.getDefault();
    private final CountDownLatch serverSocketCreated = new CountDownLatch(1);
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

    SocketAddress awaitSocketAddress() throws InterruptedException {
      assertThat(serverSocketCreated.await(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
      ServerSocket serverSocket = serverSocketReference.get();
      assertThat(serverSocket).isNotNull();
      return new InetSocketAddress(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
    }

    private ServerSocket capture(ServerSocket serverSocket) {
      serverSocketReference.set(serverSocket);
      serverSocketCreated.countDown();
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
