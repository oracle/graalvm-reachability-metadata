/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
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
import org.junit.jupiter.api.Test;

public class ConnectionBuilderInnerExtendedNioSocketOptionsTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    static {
        preferNioTransport();
    }

    @Test
    void extendedKeepAliveOptionsAreConfiguredWhenConnectingWithNioTransport() throws Exception {
        try (PingRedisServer server = new PingRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                SocketOptions.KeepAliveOptions keepAliveOptions = SocketOptions.KeepAliveOptions.builder()
                        .enable()
                        .count(3)
                        .idle(Duration.ofSeconds(15))
                        .interval(Duration.ofSeconds(5))
                        .build();
                ClientOptions clientOptions = ClientOptions.builder()
                        .autoReconnect(false)
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(Duration.ofSeconds(1))
                                .keepAlive(keepAliveOptions)
                                .build())
                        .build();

                client.setOptions(clientOptions);
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);

                assertThat(connection.sync().ping()).isEqualTo("PONG");
                assertThat(server.commands()).anySatisfy(command -> assertThat(command).containsExactly("PING"));
            } finally {
                if (connection != null) {
                    connection.close();
                }
                client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            }
        }
    }

    private static void preferNioTransport() {
        System.setProperty("io.lettuce.core.epoll", "false");
        System.setProperty("io.lettuce.core.iouring", "false");
        System.setProperty("io.lettuce.core.kqueue", "false");
    }

    private static final class PingRedisServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final List<Thread> handlerThreads = new CopyOnWriteArrayList<>();
        private volatile boolean closed;

        PingRedisServer() throws Exception {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(500);
            acceptThread = new Thread(this::acceptConnections, "extended-keepalive-redis-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
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
            for (Socket socket : sockets) {
                socket.close();
            }
            serverSocket.close();
            join(acceptThread);
            for (Thread handlerThread : handlerThreads) {
                join(handlerThread);
            }
        }

        private void acceptConnections() {
            started.countDown();
            while (!closed) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(5_000);
                    sockets.add(socket);
                    Thread handlerThread = new Thread(() -> handleQuietly(socket),
                            "extended-keepalive-redis-connection");
                    handlerThread.setDaemon(true);
                    handlerThreads.add(handlerThread);
                    handlerThread.start();
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (!closed) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        private void handleQuietly(Socket socket) {
            try (socket) {
                handle(socket);
            } catch (IOException e) {
                if (!closed) {
                    throw new IllegalStateException(e);
                }
            } finally {
                sockets.remove(socket);
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

        private static List<String> readCommand(BufferedInputStream input) throws IOException {
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

        private static String readLine(BufferedInputStream input) throws IOException {
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

        private static void writeResponse(BufferedOutputStream output, List<String> command) throws IOException {
            String commandName = command.get(0);
            switch (commandName) {
                case "HELLO":
                    writeHelloResponse(output);
                    break;
                case "PING":
                    writeSimple(output, "PONG");
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

        private static void writeHelloResponse(BufferedOutputStream output) throws IOException {
            output.write("%7\r\n".getBytes(StandardCharsets.UTF_8));
            writeSimple(output, "server");
            writeBulk(output, "redis");
            writeSimple(output, "version");
            writeBulk(output, "7.0.0");
            writeSimple(output, "proto");
            writeInteger(output, 3);
            writeSimple(output, "id");
            writeInteger(output, 1);
            writeSimple(output, "mode");
            writeBulk(output, "standalone");
            writeSimple(output, "role");
            writeBulk(output, "master");
            writeSimple(output, "modules");
            output.write("*0\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private static void writeSimple(BufferedOutputStream output, String value) throws IOException {
            output.write(("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private static void writeInteger(BufferedOutputStream output, long value) throws IOException {
            output.write((":" + Long.toString(value) + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private static void writeBulk(BufferedOutputStream output, String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private static void join(Thread thread) {
            try {
                thread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
