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

import javax.net.ServerSocketFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.net.server.ServerSocketReceiver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteAppenderStreamClientTest {

  @Test
  void serverReceiverDispatchesEventsFromAnAcceptedRemoteAppender() throws Exception {
    try (RemoteLoggingSupport logging = new RemoteLoggingSupport()) {
      BoundSocketFactory socketFactory = new BoundSocketFactory();
      ServerSocketReceiver receiver = new ServerSocketReceiver() {
        @Override
        protected ServerSocketFactory getServerSocketFactory() {
          return socketFactory;
        }
      };
      receiver.setContext(logging.context);
      receiver.setAddress(InetAddress.getLoopbackAddress().getHostAddress());
      receiver.setPort(0);
      try {
        receiver.start();
        assertThat(receiver.isStarted()).isTrue();
        try (Socket sender = new Socket()) {
          sender.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),
              socketFactory.socket.getLocalPort()), RemoteLoggingSupport.TIMEOUT_MILLIS);
          try (ObjectOutputStream output = new ObjectOutputStream(sender.getOutputStream())) {
            output.writeObject(logging.event(Level.DEBUG, "ignored event"));
            output.writeObject(logging.event(Level.ERROR, "remote order {}", "rejected"));
            output.flush();

            ILoggingEvent received = logging.awaitEvent();
            assertThat(received.getFormattedMessage()).isEqualTo("remote order rejected");
            assertThat(received.getLoggerName()).isEqualTo(logging.logger.getName());
            assertThat(received.getLevel()).isEqualTo(Level.ERROR);
            assertThat(received.getMDCPropertyMap()).containsEntry("request", "remote-request");
          }
        }
      } finally {
        receiver.stop();
        if (socketFactory.socket != null) {
          socketFactory.socket.close();
        }
      }
    }
  }

  private static final class BoundSocketFactory extends ServerSocketFactory {

    private ServerSocket socket;

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
      return createServerSocket(port, 50, InetAddress.getLoopbackAddress());
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog) throws IOException {
      return createServerSocket(port, backlog, InetAddress.getLoopbackAddress());
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress address) throws IOException {
      socket = new ServerSocket(port, backlog, address);
      socket.setSoTimeout(RemoteLoggingSupport.TIMEOUT_MILLIS);
      return socket;
    }
  }
}
