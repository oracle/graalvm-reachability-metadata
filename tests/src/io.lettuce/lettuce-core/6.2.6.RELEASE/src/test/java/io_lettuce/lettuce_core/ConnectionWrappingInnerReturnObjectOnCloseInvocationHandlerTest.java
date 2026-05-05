/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.support.ConnectionPoolSupport;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

public class ConnectionWrappingInnerReturnObjectOnCloseInvocationHandlerTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void pooledConnectionDelegatesRegularCallsAndWrapsSynchronousApi() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            client.setDefaultTimeout(COMMAND_TIMEOUT);
            GenericObjectPoolConfig<StatefulRedisConnection<String, String>> config = new GenericObjectPoolConfig<>();
            config.setMaxTotal(1);

            try (GenericObjectPool<StatefulRedisConnection<String, String>> pool =
                    ConnectionPoolSupport.createGenericObjectPool(client::connect, config, true)) {
                StatefulRedisConnection<String, String> connection = pool.borrowObject();

                assertThat(connection.isOpen()).isTrue();

                RedisCommands<String, String> commands = connection.sync();
                assertThat(commands.ping()).isEqualTo("PONG");
                assertThat(commands.getStatefulConnection()).isSameAs(connection);
                assertThat(connection.sync()).isSameAs(commands);

                connection.close();
                assertThat(pool.getNumIdle()).isEqualTo(1);
            } finally {
                client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            }

            assertThat(server.commands()).extracting(command -> command.get(0)).contains("PING");
        }
    }

    private static final class FakeRedisServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private volatile boolean closed;
        private volatile Socket activeSocket;

        FakeRedisServer() throws Exception {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(500);
            thread = new Thread(this::acceptConnections, "connection-wrapping-redis-server");
            thread.setDaemon(true);
            thread.start();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        }

        RedisURI redisUri() {
            return RedisURI.Builder.redis(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort())
                    .withTimeout(COMMAND_TIMEOUT)
                    .build();
        }

        List<List<String>> commands() {
            return commands;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
            Socket socket = activeSocket;
            if (socket != null) {
                socket.close();
            }
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void acceptConnections() {
            started.countDown();
            while (!closed) {
                try (Socket socket = serverSocket.accept()) {
                    activeSocket = socket;
                    socket.setSoTimeout(1_000);
                    handle(socket);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (!closed) {
                        throw new IllegalStateException(e);
                    }
                } finally {
                    activeSocket = null;
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            while (!closed && !socket.isClosed()) {
                List<String> command;
                try {
                    command = readCommand(input);
                } catch (EOFException | SocketTimeoutException e) {
                    return;
                }
                commands.add(command);
                writeResponse(output, command);
                output.flush();
            }
        }

        private List<String> readCommand(BufferedInputStream input) throws IOException {
            String firstLine = readLine(input);
            if (!firstLine.startsWith("*")) {
                throw new IOException("Expected RESP array but got: " + firstLine);
            }
            int count = Integer.parseInt(firstLine.substring(1));
            List<String> command = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String bulkHeader = readLine(input);
                if (!bulkHeader.startsWith("$")) {
                    throw new IOException("Expected RESP bulk string but got: " + bulkHeader);
                }
                int length = Integer.parseInt(bulkHeader.substring(1));
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length || input.read() != '\r' || input.read() != '\n') {
                    throw new EOFException();
                }
                String value = new String(bytes, StandardCharsets.UTF_8);
                command.add(i == 0 ? value.toUpperCase() : value);
            }
            return command;
        }

        private String readLine(BufferedInputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                int next = input.read();
                if (next == -1) {
                    throw new EOFException();
                }
                if (next == '\r') {
                    int lineFeed = input.read();
                    if (lineFeed != '\n') {
                        throw new IOException("Expected LF after CR");
                    }
                    return bytes.toString(StandardCharsets.UTF_8);
                }
                bytes.write(next);
            }
        }

        private void writeResponse(OutputStream output, List<String> command) throws IOException {
            switch (command.get(0)) {
                case "PING":
                    writeSimple(output, "PONG");
                    break;
                case "HELLO":
                    writeHelloResponse(output);
                    break;
                case "AUTH":
                case "CLIENT":
                case "SELECT":
                case "QUIT":
                    writeSimple(output, "OK");
                    break;
                default:
                    writeSimple(output, "OK");
                    break;
            }
        }

        private void writeHelloResponse(OutputStream output) throws IOException {
            output.write("%6\r\n".getBytes(StandardCharsets.UTF_8));
            writeBulk(output, "server");
            writeBulk(output, "redis");
            writeBulk(output, "version");
            writeBulk(output, "7.0.0");
            writeBulk(output, "proto");
            writeInteger(output, 3);
            writeBulk(output, "id");
            writeInteger(output, 1);
            writeBulk(output, "mode");
            writeBulk(output, "standalone");
            writeBulk(output, "role");
            writeBulk(output, "master");
        }

        private void writeSimple(OutputStream output, String value) throws IOException {
            output.write(("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeInteger(OutputStream output, long value) throws IOException {
            output.write((":" + Long.toString(value) + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeBulk(OutputStream output, String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
    }
}
