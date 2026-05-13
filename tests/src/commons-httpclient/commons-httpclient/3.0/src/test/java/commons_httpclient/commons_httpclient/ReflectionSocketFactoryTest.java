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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.httpclient.protocol.ReflectionSocketFactory;
import org.junit.jupiter.api.Test;

public class ReflectionSocketFactoryTest {
    @Test
    void createsBoundSocketWithJdkSocketFactory() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicReference<IOException> acceptFailure = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            Thread acceptThread = new Thread(() -> acceptOneConnection(serverSocket, accepted, acceptFailure),
                    "commons-httpclient-reflection-socket-accept");
            acceptThread.start();

            try (Socket socket = ReflectionSocketFactory.createSocket("javax.net.SocketFactory",
                    loopbackAddress.getHostAddress(), serverSocket.getLocalPort(), loopbackAddress, 0, 5000)) {
                assertThat(socket).isNotNull();
                assertThat(socket.isConnected()).isTrue();
                assertThat(socket.getLocalAddress()).isEqualTo(loopbackAddress);
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
}
