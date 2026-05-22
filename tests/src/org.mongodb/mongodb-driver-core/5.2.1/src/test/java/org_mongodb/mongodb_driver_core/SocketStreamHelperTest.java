/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongodb_driver_core;

import com.mongodb.ServerAddress;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import com.mongodb.internal.TimeoutSettings;
import com.mongodb.internal.connection.OperationContext;
import com.mongodb.internal.connection.SocketStreamFactory;
import com.mongodb.internal.connection.Stream;
import com.mongodb.spi.dns.InetAddressResolver;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketStreamHelperTest {
    @Test
    void socketStreamOpenConfiguresAndConnectsSocket() throws Exception {
        final InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            serverSocket.setSoTimeout(5_000);
            final Future<Socket> acceptedSocket = executorService.submit(serverSocket::accept);
            final SocketSettings socketSettings = SocketSettings.builder()
                    .receiveBufferSize(1024)
                    .sendBufferSize(2048)
                    .build();
            final SslSettings sslSettings = SslSettings.builder().build();
            final TimeoutSettings timeoutSettings = new TimeoutSettings(5_000, 250, 750, null, 5_000);
            final InetAddressResolver inetAddressResolver = host -> Collections.singletonList(loopbackAddress);
            final SocketStreamFactory streamFactory = new SocketStreamFactory(
                    inetAddressResolver, socketSettings, sslSettings);
            final Stream stream = streamFactory.create(new ServerAddress("localhost", serverSocket.getLocalPort()));

            stream.open(OperationContext.simpleOperationContext(timeoutSettings, null));
            try (Socket socket = acceptedSocket.get(5, TimeUnit.SECONDS)) {
                assertThat(stream.isClosed()).isFalse();
                assertThat(stream.getAddress().getPort()).isEqualTo(serverSocket.getLocalPort());
                assertThat(socket.isConnected()).isTrue();
            } finally {
                stream.close();
            }

            assertThat(stream.isClosed()).isTrue();
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
