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
import com.mongodb.internal.connection.PowerOfTwoBufferPool;
import com.mongodb.internal.connection.SocketStream;
import com.mongodb.internal.connection.Stream;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class SocketStreamHelperTest {
    @Test
    void socketStreamOpenConfiguresJava11ExtendedKeepAliveOptions() throws Exception {
        final RecordingSocket socket = new RecordingSocket();
        final SocketFactory socketFactory = new SingleSocketFactory(socket);
        final SocketSettings socketSettings = SocketSettings.builder()
                .connectTimeout(250, TimeUnit.MILLISECONDS)
                .readTimeout(750, TimeUnit.MILLISECONDS)
                .receiveBufferSize(1024)
                .sendBufferSize(2048)
                .build();
        final SslSettings sslSettings = SslSettings.builder().build();
        final Stream stream = new SocketStream(new ServerAddress("127.0.0.1", 27017),
                host -> Collections.singletonList(InetAddress.getByName(host)), socketSettings, sslSettings, socketFactory,
                PowerOfTwoBufferPool.DEFAULT);
        final OperationContext operationContext = OperationContext.simpleOperationContext(
                new TimeoutSettings(5_000, 250, 750, null, 5_000), null);

        stream.open(operationContext);
        try {
            assertThat(stream.isClosed()).isFalse();
            assertThat(socket.connected).isTrue();
            assertThat(socket.connectTimeout).isEqualTo(250);
            assertThat(socket.tcpNoDelay).isTrue();
            assertThat(socket.keepAlive).isTrue();
            assertThat(socket.soTimeout).isEqualTo(750);
            assertThat(socket.receiveBufferSize).isEqualTo(1024);
            assertThat(socket.sendBufferSize).isEqualTo(2048);
            assertThat(socket.extendedOptions)
                    .containsEntry("TCP_KEEPCOUNT", 9)
                    .containsEntry("TCP_KEEPIDLE", 120)
                    .containsEntry("TCP_KEEPINTERVAL", 10);
        } finally {
            stream.close();
        }

        assertThat(socket.closed).isTrue();
    }

    private static final class SingleSocketFactory extends SocketFactory {
        private final Socket socket;

        private SingleSocketFactory(final Socket socket) {
            this.socket = socket;
        }

        @Override
        public Socket createSocket() {
            return socket;
        }

        @Override
        public Socket createSocket(final String host, final int port) {
            return socket;
        }

        @Override
        public Socket createSocket(final String host, final int port, final InetAddress localHost,
                                   final int localPort) {
            return socket;
        }

        @Override
        public Socket createSocket(final InetAddress host, final int port) {
            return socket;
        }

        @Override
        public Socket createSocket(final InetAddress address, final int port, final InetAddress localAddress,
                                   final int localPort) {
            return socket;
        }
    }

    private static final class RecordingSocket extends Socket {
        private final Map<String, Object> extendedOptions = new LinkedHashMap<>();
        private final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private boolean connected;
        private boolean closed;
        private boolean tcpNoDelay;
        private boolean keepAlive;
        private int soTimeout;
        private int receiveBufferSize;
        private int sendBufferSize;
        private int connectTimeout;

        @Override
        public void connect(final SocketAddress endpoint, final int timeout) {
            connected = true;
            connectTimeout = timeout;
        }

        @Override
        public void setTcpNoDelay(final boolean on) {
            tcpNoDelay = on;
        }

        @Override
        public void setSoTimeout(final int timeout) {
            soTimeout = timeout;
        }

        @Override
        public void setKeepAlive(final boolean on) {
            keepAlive = on;
        }

        @Override
        public void setReceiveBufferSize(final int size) {
            receiveBufferSize = size;
        }

        @Override
        public void setSendBufferSize(final int size) {
            sendBufferSize = size;
        }

        @Override
        public <T> Socket setOption(final SocketOption<T> name, final T value) throws IOException {
            extendedOptions.put(name.name(), value);
            return this;
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public synchronized void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }
}
