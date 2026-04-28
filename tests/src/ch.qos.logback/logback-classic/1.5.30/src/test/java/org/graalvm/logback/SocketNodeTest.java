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
import ch.qos.logback.classic.net.SimpleSocketServer;
import ch.qos.logback.classic.net.SocketNode;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketNodeTest {

  private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

  private LoggerContext context;
  private Logger remoteLogger;
  private CapturingAppender appender;

  @BeforeEach
  void setUp() {
    context = new LoggerContext();
    context.setMDCAdapter(new LogbackMDCAdapter());
    context.setName("socket-node-context");
    context.start();

    remoteLogger = context.getLogger("org.graalvm.logback.socket-node");
    remoteLogger.setAdditive(false);
    remoteLogger.setLevel(Level.INFO);

    appender = new CapturingAppender();
    appender.setContext(context);
    appender.start();
    remoteLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    if (remoteLogger != null && appender != null) {
      remoteLogger.detachAppender(appender);
    }
    if (appender != null) {
      appender.stop();
    }
    if (context != null) {
      context.stop();
    }
  }

  @Test
  void readsSerializedLoggingEventFromSocketAndDispatchesIt() throws Exception {
    String message = "socket node dispatches remote event";
    SocketNode socketNode;
    Thread socketNodeThread;

    try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket serverSocket = listener.accept()) {
      serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
      socketNode = new SocketNode(new SimpleSocketServer(context, listener.getLocalPort()), serverSocket, context);
      socketNodeThread = new Thread(socketNode, "logback-socket-node-test");
      socketNodeThread.start();

      try (ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream())) {
        outputStream.writeObject(createRemoteEvent(message));
        outputStream.flush();
      }

      assertThat(appender.awaitEvent()).isTrue();
      socketNodeThread.join(SOCKET_TIMEOUT_MILLIS);
    }

    assertThat(socketNodeThread.isAlive()).isFalse();
    ILoggingEvent receivedEvent = appender.getEvent();
    assertThat(receivedEvent).isNotNull();
    assertThat(receivedEvent.getLoggerName()).isEqualTo(remoteLogger.getName());
    assertThat(receivedEvent.getLevel()).isEqualTo(Level.INFO);
    assertThat(receivedEvent.getFormattedMessage()).isEqualTo(message);
  }

  private LoggingEventVO createRemoteEvent(String message) {
    LoggingEvent event = new LoggingEvent(
        SocketNodeTest.class.getName(),
        remoteLogger,
        Level.INFO,
        message,
        null,
        null);
    event.setThreadName("socket-node-client");
    event.prepareForDeferredProcessing();
    return LoggingEventVO.build(event);
  }

  private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

    private final CountDownLatch eventReceived = new CountDownLatch(1);
    private final AtomicReference<ILoggingEvent> event = new AtomicReference<>();

    @Override
    protected void append(ILoggingEvent eventObject) {
      event.set(eventObject);
      eventReceived.countDown();
    }

    private boolean awaitEvent() throws InterruptedException {
      return eventReceived.await(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private ILoggingEvent getEvent() {
      return event.get();
    }
  }
}
