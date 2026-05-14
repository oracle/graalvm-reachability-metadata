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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.net.SocketConnector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketReceiverTest {

  private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

  @Test
  void dispatchesSerializedLoggingEventFromRemoteSocket() throws Exception {
    LoggerContext loggerContext = new LoggerContext();
    loggerContext.setName("socket-receiver-test");
    loggerContext.setMDCAdapter(new LogbackMDCAdapter());

    OneShotSocketReceiver receiver = null;
    CapturingAppender appender = new CapturingAppender();
    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Socket writerSocket = new Socket()) {
      serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
      writerSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()),
          SOCKET_TIMEOUT_MILLIS);

      try (Socket receiverSocket = serverSocket.accept()) {
        Logger remoteLogger = loggerContext.getLogger("org.graalvm.logback.SocketReceiverTest.remote");
        remoteLogger.setAdditive(false);
        remoteLogger.setLevel(Level.INFO);
        appender.setContext(loggerContext);
        appender.start();
        remoteLogger.addAppender(appender);

        receiver = new OneShotSocketReceiver(receiverSocket);
        receiver.setContext(loggerContext);
        receiver.setRemoteHost(InetAddress.getLoopbackAddress().getHostAddress());
        receiver.setPort(serverSocket.getLocalPort());
        receiver.setReconnectionDelay(1);
        receiver.setAcceptConnectionTimeout(SOCKET_TIMEOUT_MILLIS);
        receiver.start();

        LoggingEventVO loggingEvent = createLoggingEvent(remoteLogger, "message from socket receiver");
        try (ObjectOutputStream outputStream = new ObjectOutputStream(writerSocket.getOutputStream())) {
          outputStream.writeObject(loggingEvent);
          outputStream.flush();
        }

        ILoggingEvent receivedEvent = appender.awaitEvent();
        assertThat(receivedEvent.getFormattedMessage()).isEqualTo("message from socket receiver");
      }
    } finally {
      if (receiver != null) {
        receiver.stop();
      }
      appender.stop();
      loggerContext.stop();
    }
  }

  private static LoggingEventVO createLoggingEvent(Logger logger, String message) {
    LoggingEvent loggingEvent = new LoggingEvent(SocketReceiverTest.class.getName(), logger, Level.INFO, message, null,
        null);
    return LoggingEventVO.build(loggingEvent);
  }

  private static final class OneShotSocketReceiver extends SocketReceiver {

    private final AtomicReference<Socket> socketReference;

    OneShotSocketReceiver(Socket socket) {
      socketReference = new AtomicReference<>(socket);
    }

    @Override
    protected SocketConnector newConnector(InetAddress address, int port, int initialDelay, int retryDelay) {
      return new SocketConnector() {
        @Override
        public Socket call() {
          return socketReference.getAndSet(null);
        }

        @Override
        public void setExceptionHandler(ExceptionHandler exceptionHandler) {
          // This one-shot connector cannot fail before returning its socket.
        }

        @Override
        public void setSocketFactory(SocketFactory socketFactory) {
          // The connected socket is supplied by the test fixture.
        }
      };
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
