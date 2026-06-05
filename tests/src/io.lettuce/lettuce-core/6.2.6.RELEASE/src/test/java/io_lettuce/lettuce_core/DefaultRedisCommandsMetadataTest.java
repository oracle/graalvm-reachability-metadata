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
import io.lettuce.core.dynamic.Commands;
import io.lettuce.core.dynamic.RedisCommandFactory;
import io.lettuce.core.dynamic.annotation.Command;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class DefaultRedisCommandsMetadataTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void dynamicCommandFactoryDiscoversInheritedCommandInterfaceMethods() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);

                RedisCommandFactory factory = new RedisCommandFactory(connection);
                InheritedCommands commands = factory.getCommands(InheritedCommands.class);

                assertThat(commands.setValue("metadata-key", "metadata-value")).isEqualTo("OK");
                assertThat(commands.getValue("metadata-key")).isEqualTo("metadata-value");
                assertThat(server.commands()).extracting(command -> command.get(0))
                        .contains("COMMAND", "SET", "GET");
            } finally {
                closeConnection(connection);
                shutdown(client);
            }
        }
    }

    public interface BaseCommands extends Commands {

        @Command("GET ?0")
        String getValue(String key);

        @Command("SET ?0 ?1")
        String setValue(String key, String value);
    }

    public interface InheritedCommands extends BaseCommands {
    }

    private static void shutdown(RedisClient client) {
        client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
    }

    private static void closeConnection(StatefulRedisConnection<String, String> connection) {
        if (connection != null) {
            connection.close();
        }
    }

    private static final class FakeRedisServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private final Map<String, String> strings = Collections.synchronizedMap(new LinkedHashMap<>());
        private volatile boolean closed;

        FakeRedisServer() throws Exception {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(500);
            thread = new Thread(this::acceptConnections, "metadata-fake-redis-server");
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
                    socket.setSoTimeout(5_000);
                    handle(socket);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (!closed) {
                        throw new IllegalStateException(e);
                    }
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
                } catch (EOFException e) {
                    return;
                } catch (SocketTimeoutException e) {
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
            String name = command.get(0);
            switch (name) {
                case "PING":
                    writeSimple(output, "PONG");
                    break;
                case "HELLO":
                    writeHelloResponse(output);
                    break;
                case "COMMAND":
                    writeEmptyArray(output);
                    break;
                case "AUTH":
                case "CLIENT":
                case "SELECT":
                case "QUIT":
                    writeSimple(output, "OK");
                    break;
                case "SET":
                    strings.put(command.get(1), command.get(2));
                    writeSimple(output, "OK");
                    break;
                case "GET":
                    writeBulk(output, strings.get(command.get(1)));
                    break;
                default:
                    writeSimple(output, "OK");
                    break;
            }
        }

        private void writeHelloResponse(OutputStream output) throws IOException {
            output.write("%4\r\n".getBytes(StandardCharsets.UTF_8));
            writeSimple(output, "id");
            writeInteger(output, 1);
            writeSimple(output, "mode");
            writeSimple(output, "standalone");
            writeSimple(output, "version");
            writeSimple(output, "7.0.0");
            writeSimple(output, "role");
            writeSimple(output, "master");
        }

        private void writeSimple(OutputStream output, String value) throws IOException {
            output.write(("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeInteger(OutputStream output, long value) throws IOException {
            output.write((":" + Long.toString(value) + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeBulk(OutputStream output, String value) throws IOException {
            if (value == null) {
                output.write("$-1\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private void writeEmptyArray(OutputStream output) throws IOException {
            output.write("*0\r\n".getBytes(StandardCharsets.UTF_8));
        }
    }
}
