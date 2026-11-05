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
import ch.qos.logback.classic.net.SimpleSocketServer;
import ch.qos.logback.classic.net.SocketNode;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketNodeTest {

  @Test
  void dispatchesRemoteEventsAccordingToTheLocalLoggerLevel() throws Exception {
    try (RemoteLoggingSupport logging = new RemoteLoggingSupport();
        ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Socket sender = new Socket()) {
      listener.setSoTimeout(RemoteLoggingSupport.TIMEOUT_MILLIS);
      sender.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()),
          RemoteLoggingSupport.TIMEOUT_MILLIS);
      try (Socket accepted = listener.accept();
          ObjectOutputStream output = new ObjectOutputStream(sender.getOutputStream())) {
        accepted.setSoTimeout(RemoteLoggingSupport.TIMEOUT_MILLIS);
        output.writeObject(logging.event(Level.DEBUG, "ignored event"));
        output.writeObject(logging.event(Level.INFO, "accepted order {}", 42));
        output.flush();
        sender.shutdownOutput();

        SimpleSocketServer server = new SimpleSocketServer(logging.context, listener.getLocalPort());
        try {
          new SocketNode(server, accepted, logging.context).run();
        } finally {
          server.close();
        }

        ILoggingEvent received = logging.awaitEvent();
        assertThat(received.getFormattedMessage()).isEqualTo("accepted order 42");
        assertThat(received.getLoggerName()).isEqualTo(logging.logger.getName());
        assertThat(received.getLevel()).isEqualTo(Level.INFO);
        assertThat(received.getMDCPropertyMap()).containsEntry("request", "remote-request");
      }
    }
  }
}
