/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.net.server.AbstractServerSocketAppender;
import ch.qos.logback.core.spi.PreSerializationTransformer;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ServerSocketFactory;
import org.junit.jupiter.api.Test;

public class RemoteReceiverStreamClientTest {

    @Test
    void writesQueuedEventsToConnectedReceiverClient() throws Exception {
        ContextBase context = new ContextBase();
        context.start();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            TestServerSocketAppender appender = new TestServerSocketAppender(serverSocket);
            appender.setContext(context);
            appender.setName("remoteReceiver");
            appender.start();

            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort())) {
                socket.setSoTimeout(5_000);

                try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
                    appender.doAppend("logback-core remote receiver event");

                    assertThat(inputStream.readObject()).isEqualTo("logback-core remote receiver event");
                }
            } finally {
                appender.stop();
            }
        } finally {
            context.stop();
        }
    }

    private static final class TestServerSocketAppender extends AbstractServerSocketAppender<Serializable> {

        private final ServerSocketFactory serverSocketFactory;

        private TestServerSocketAppender(ServerSocket serverSocket) {
            this.serverSocketFactory = new PreparedServerSocketFactory(serverSocket);
        }

        @Override
        protected void postProcessEvent(Serializable event) {
        }

        @Override
        protected PreSerializationTransformer<Serializable> getPST() {
            return event -> event;
        }

        @Override
        protected ServerSocketFactory getServerSocketFactory() {
            return serverSocketFactory;
        }
    }

    private static final class PreparedServerSocketFactory extends ServerSocketFactory {

        private final ServerSocket serverSocket;
        private boolean used;

        private PreparedServerSocketFactory(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        @Override
        public ServerSocket createServerSocket(int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
            if (used) {
                throw new IllegalStateException("Server socket already created");
            }
            used = true;
            return serverSocket;
        }
    }
}
