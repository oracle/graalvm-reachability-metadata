/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.net.server.AbstractServerSocketAppender;
import ch.qos.logback.core.spi.PreSerializationTransformer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ServerSocketFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteReceiverStreamClientTest {

    private static final int TIMEOUT_MILLIS = 5_000;

    @Test
    void writesSerializedEventToConnectedReceiver() throws Exception {
        ContextBase context = new ContextBase();
        context.setName("remote-receiver-stream-client-test-context");
        LoopbackServerSocketFactory serverSocketFactory = new LoopbackServerSocketFactory();
        StringServerSocketAppender appender = new StringServerSocketAppender(serverSocketFactory);
        appender.setContext(context);
        appender.setName("remote-receiver-stream-client-test");
        appender.setPort(0);

        try {
            appender.start();

            assertThat(appender.isStarted()).isTrue();

            try (Socket receiverSocket = new Socket()) {
                receiverSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                        serverSocketFactory.awaitPort()), TIMEOUT_MILLIS);
                receiverSocket.setSoTimeout(TIMEOUT_MILLIS);

                try (ObjectInputStream inputStream = new ObjectInputStream(
                        receiverSocket.getInputStream())) {
                    appender.doAppend("remote-receiver-payload");

                    assertThat(inputStream.readObject()).isEqualTo("remote-receiver-payload");
                }
            }
        } finally {
            appender.stop();
            context.stop();
        }
    }

    private static final class StringServerSocketAppender
            extends AbstractServerSocketAppender<String> {

        private final ServerSocketFactory serverSocketFactory;

        private StringServerSocketAppender(ServerSocketFactory serverSocketFactory) {
            this.serverSocketFactory = serverSocketFactory;
        }

        @Override
        protected void postProcessEvent(String event) {
        }

        @Override
        protected PreSerializationTransformer<String> getPST() {
            return event -> event;
        }

        @Override
        protected ServerSocketFactory getServerSocketFactory() {
            return serverSocketFactory;
        }
    }

    private static final class LoopbackServerSocketFactory extends ServerSocketFactory {

        private final CountDownLatch socketCreated = new CountDownLatch(1);

        private volatile int port;

        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            return createLoopbackServerSocket(0, 50);
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog) throws IOException {
            return createLoopbackServerSocket(0, backlog);
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress)
                throws IOException {
            return createLoopbackServerSocket(0, backlog);
        }

        private ServerSocket createLoopbackServerSocket(int port, int backlog)
                throws IOException {
            ServerSocket serverSocket = new ServerSocket(port, backlog,
                    InetAddress.getLoopbackAddress());
            this.port = serverSocket.getLocalPort();
            socketCreated.countDown();
            return serverSocket;
        }

        private int awaitPort() throws InterruptedException {
            assertThat(socketCreated.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            return port;
        }
    }
}
