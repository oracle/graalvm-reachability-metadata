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
import ch.qos.logback.classic.net.SocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketReceiverTest {

  @Test
  void connectsToARemoteAppenderAndDispatchesItsEvents() throws Exception {
    try (RemoteLoggingSupport logging = new RemoteLoggingSupport();
        ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      listener.setSoTimeout(RemoteLoggingSupport.TIMEOUT_MILLIS);
      SocketReceiver receiver = new SocketReceiver();
      receiver.setContext(logging.context);
      receiver.setRemoteHost(InetAddress.getLoopbackAddress().getHostAddress());
      receiver.setPort(listener.getLocalPort());
      receiver.setAcceptConnectionTimeout(RemoteLoggingSupport.TIMEOUT_MILLIS);
      receiver.setReconnectionDelay(RemoteLoggingSupport.TIMEOUT_MILLIS);
      try {
        receiver.start();
        assertThat(receiver.isStarted()).isTrue();
        try (Socket connection = listener.accept();
            ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream())) {
          output.writeObject(logging.event(Level.DEBUG, "ignored event"));
          output.writeObject(logging.event(Level.WARN, "received shipment {}", "parcel"));
          output.flush();

          ILoggingEvent received = logging.awaitEvent();
          assertThat(received.getFormattedMessage()).isEqualTo("received shipment parcel");
          assertThat(received.getLoggerName()).isEqualTo(logging.logger.getName());
          assertThat(received.getLevel()).isEqualTo(Level.WARN);
          assertThat(received.getThreadName()).isEqualTo("remote-worker");
          receiver.stop();
          assertThat(receiver.isStarted()).isFalse();
        }
      } finally {
        receiver.stop();
      }
    }
  }
}
