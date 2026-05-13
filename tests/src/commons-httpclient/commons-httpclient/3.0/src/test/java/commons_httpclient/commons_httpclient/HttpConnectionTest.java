/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpConnection;
import org.junit.jupiter.api.Test;

public class HttpConnectionTest {
    @Test
    void shutdownOutputClosesClientWriteSide() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            FutureTask<ServerObservation> serverRead = new FutureTask<>(
                    () -> readUntilEndOfStream(serverSocket));
            Thread serverThread = new Thread(serverRead, "commons-httpclient-shutdown-output-server");
            serverThread.start();

            HttpConnection connection = new HttpConnection(loopbackAddress.getHostAddress(),
                    serverSocket.getLocalPort());
            connection.getParams().setSoTimeout(5000);

            try {
                connection.open();
                connection.write(new byte[] {'p'});
                connection.flushRequestOutputStream();

                connection.shutdownOutput();

                ServerObservation observation = serverRead.get(5, TimeUnit.SECONDS);
                assertThat(observation.firstByte).isEqualTo('p');
                assertThat(observation.endOfStreamReached).isTrue();
            } finally {
                connection.close();
                serverSocket.close();
                serverThread.join(TimeUnit.SECONDS.toMillis(5));
            }

            assertThat(serverThread.isAlive()).isFalse();
        }
    }

    private static ServerObservation readUntilEndOfStream(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(5000);
            InputStream input = socket.getInputStream();
            int firstByte = input.read();
            int currentByte = firstByte;
            while (currentByte != -1) {
                currentByte = input.read();
            }
            return new ServerObservation(firstByte, true);
        }
    }

    private static final class ServerObservation {
        private final int firstByte;
        private final boolean endOfStreamReached;

        private ServerObservation(int firstByte, boolean endOfStreamReached) {
            this.firstByte = firstByte;
            this.endOfStreamReached = endOfStreamReached;
        }
    }
}
