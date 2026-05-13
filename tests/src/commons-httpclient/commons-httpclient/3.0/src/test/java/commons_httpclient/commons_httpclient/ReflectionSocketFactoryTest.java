/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.protocol.ReflectionSocketFactory;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReflectionSocketFactoryTest {
    @Test
    @Order(1)
    void createsSocketWithPublicFactoryClassName() throws Exception {
        Socket socket = ReflectionSocketFactory.createSocket(RecordingSocketFactory.class.getName(), "127.0.0.1", 80,
                InetAddress.getLoopbackAddress(), 0, 250);
        if (socket == null) {
            return;
        }
        try (Socket ignored = socket) {
            assertThat(socket).isInstanceOf(RecordingSocket.class);
            RecordingSocket recordingSocket = (RecordingSocket) socket;
            assertThat(recordingSocket.bound).isTrue();
            assertThat(recordingSocket.connected).isTrue();
        }
    }

    @Test
    @Order(2)
    void translatesReflectedSocketTimeoutException() throws Exception {
        try {
            Socket socket = ReflectionSocketFactory.createSocket(TimeoutSocketFactory.class.getName(), "127.0.0.1", 80,
                    InetAddress.getLoopbackAddress(), 0, 250);
            assertThat(socket).isNull();
        } catch (ConnectTimeoutException e) {
            assertThat(e.getCause()).isInstanceOf(SocketTimeoutException.class);
            assertThat(e.getMessage()).contains("250 ms");
        }
    }

    @Order(3)
    @Test
    void createsBoundSocketWithJdkSocketFactory() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicReference<IOException> acceptFailure = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            Thread acceptThread = new Thread(() -> acceptOneConnection(serverSocket, accepted, acceptFailure),
                    "commons-httpclient-reflection-socket-accept");
            acceptThread.start();

            Socket socket = ReflectionSocketFactory.createSocket("javax.net.SocketFactory",
                    loopbackAddress.getHostAddress(), serverSocket.getLocalPort(), loopbackAddress, 0, 5000);
            if (socket == null) {
                serverSocket.close();
                acceptThread.join(TimeUnit.SECONDS.toMillis(5));
                assertThat(acceptThread.isAlive()).isFalse();
                return;
            }
            try (Socket connectedSocket = socket) {
                assertThat(connectedSocket.isConnected()).isTrue();
                assertThat(connectedSocket.getLocalAddress()).isEqualTo(loopbackAddress);
                assertThat(accepted.await(5, TimeUnit.SECONDS)).isTrue();
            }

            acceptThread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(acceptThread.isAlive()).isFalse();
            assertThat(acceptFailure.get()).isNull();
        }
    }

    private static void acceptOneConnection(ServerSocket serverSocket, CountDownLatch accepted,
            AtomicReference<IOException> acceptFailure) {
        try (Socket ignored = serverSocket.accept()) {
            accepted.countDown();
        } catch (IOException e) {
            acceptFailure.set(e);
        }
    }

    public static final class RecordingSocketFactory {
        public static RecordingSocketFactory getDefault() {
            return new RecordingSocketFactory();
        }

        public Socket createSocket() {
            return new RecordingSocket();
        }
    }

    public static final class TimeoutSocketFactory {
        public static TimeoutSocketFactory getDefault() {
            return new TimeoutSocketFactory();
        }

        public Socket createSocket() {
            return new TimeoutSocket();
        }
    }

    private static final class RecordingSocket extends Socket {
        private boolean bound;
        private boolean connected;

        @Override
        public void bind(SocketAddress bindpoint) {
            bound = true;
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) {
            connected = true;
        }
    }

    private static final class TimeoutSocket extends Socket {
        @Override
        public void bind(SocketAddress bindpoint) {
            // The test socket never opens a real network connection.
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            throw new SocketTimeoutException("synthetic timeout");
        }
    }
}
