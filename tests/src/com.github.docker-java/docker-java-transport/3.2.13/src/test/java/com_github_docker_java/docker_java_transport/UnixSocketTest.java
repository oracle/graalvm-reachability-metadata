/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport;

import com.github.dockerjava.transport.UnixSocket;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class UnixSocketTest {
    private static final byte[] REQUEST = "ping".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE = "pong".getBytes(StandardCharsets.UTF_8);

    @Test
    void connectsToUnixDomainSocketAndExchangesBytes() throws Exception {
        Path socketDirectory = Files.createTempDirectory("docker-java-transport-");
        Path socketPath = socketDirectory.resolve("docker.sock");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            SocketAddress serverAddress = UnixDomainSocketAddress.of(socketPath);
            server.bind(serverAddress);
            Future<byte[]> clientRequest = executor.submit(() -> exchangeWithClient(server));

            try (Socket socket = UnixSocket.get(socketPath.toString())) {
                assertThat(socket).isInstanceOf(UnixSocket.class);
                assertThat(socket.getLocalSocketAddress()).isEqualTo(serverAddress);
                assertThat(socket.getRemoteSocketAddress()).isEqualTo(serverAddress);

                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(REQUEST);
                outputStream.flush();

                assertThat(socket.getInputStream().readNBytes(RESPONSE.length)).isEqualTo(RESPONSE);
            }

            assertThat(clientRequest.get(5, TimeUnit.SECONDS)).isEqualTo(REQUEST);
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(socketPath);
            Files.deleteIfExists(socketDirectory);
        }
    }

    private static byte[] exchangeWithClient(ServerSocketChannel server) throws Exception {
        try (SocketChannel client = server.accept()) {
            byte[] request = readFully(client, REQUEST.length);
            writeFully(client, ByteBuffer.wrap(RESPONSE));
            return request;
        }
    }

    private static byte[] readFully(SocketChannel channel, int length) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            int bytesRead = channel.read(buffer);
            if (bytesRead < 0) {
                throw new EOFException("Expected more bytes from the Unix socket client");
            }
        }
        return buffer.array();
    }

    private static void writeFully(SocketChannel channel, ByteBuffer buffer) throws Exception {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
