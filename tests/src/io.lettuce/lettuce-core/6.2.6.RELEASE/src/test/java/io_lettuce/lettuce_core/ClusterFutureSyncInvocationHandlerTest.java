/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.NodeSelection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
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
import org.junit.jupiter.api.Test;

public class ClusterFutureSyncInvocationHandlerTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final String NODE_ID = "07c37dfeb2352e0b216f091f3a292e88d50f3fcd";

    @Test
    void clusterSyncProxyInvokesAsyncCommandsNodeConnectionsAndSelections() throws Exception {
        try (ClusterRedisServer server = new ClusterRedisServer()) {
            RedisClusterClient client = RedisClusterClient.create(server.redisUri());
            StatefulRedisClusterConnection<String, String> connection = null;
            try {
                client.setDefaultTimeout(COMMAND_TIMEOUT);
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);

                RedisAdvancedClusterCommands<String, String> commands = connection.sync();
                assertThat(commands.ping()).isEqualTo("PONG");

                RedisClusterCommands<String, String> nodeCommands = commands.getConnection(NODE_ID);
                assertThat(nodeCommands.ping()).isEqualTo("PONG");

                NodeSelection<String, String> selection = commands.nodes(redisClusterNode -> true);
                assertThat(selection.size()).isEqualTo(1);
                assertThat(selection.commands().ping()).containsExactly("PONG");

                assertThat(server.commands()).contains(List.of("CLUSTER", "NODES"), List.of("PING"));
            } finally {
                if (connection != null) {
                    connection.close();
                }
                client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            }
        }
    }

    private static final class ClusterRedisServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final List<Thread> workers = new CopyOnWriteArrayList<>();
        private volatile boolean closed;

        ClusterRedisServer() throws Exception {
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            serverSocket.setSoTimeout(500);
            acceptThread = new Thread(this::acceptConnections, "fake-cluster-redis-server");
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
            serverSocket.close();
            for (Socket socket : sockets) {
                socket.close();
            }
            join(acceptThread);
            for (Thread worker : workers) {
                join(worker);
            }
        }

        private void acceptConnections() {
            started.countDown();
            while (!closed) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(5_000);
                    sockets.add(socket);
                    Thread worker = new Thread(() -> handleQuietly(socket), "fake-cluster-redis-client");
                    worker.setDaemon(true);
                    workers.add(worker);
                    worker.start();
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
            String name = command.get(0);
            switch (name) {
                case "PING":
                    writeSimple(output, "PONG");
                    break;
                case "CLUSTER":
                    writeClusterResponse(output, command);
                    break;
                case "INFO":
                    writeBulk(output, "# Server\r\nredis_version:7.0.0\r\n# Clients\r\nconnected_clients:1\r\n"
                            + "# Replication\r\nmaster_repl_offset:0\r\n# Cluster\r\ncluster_enabled:1\r\n");
                    break;
                case "HELLO":
                    writeHelloResponse(output);
                    break;
                case "AUTH":
                case "CLIENT":
                case "READONLY":
                case "READWRITE":
                case "SELECT":
                case "QUIT":
                    writeSimple(output, "OK");
                    break;
                default:
                    writeSimple(output, "OK");
                    break;
            }
        }

        private void writeClusterResponse(OutputStream output, List<String> command) throws IOException {
            if (command.size() > 1 && "NODES".equalsIgnoreCase(command.get(1))) {
                String host = serverSocket.getInetAddress().getHostAddress();
                int port = serverSocket.getLocalPort();
                writeBulk(output, NODE_ID + " " + host + ":" + port + "@" + (port + 10000)
                        + " myself,master - 0 0 1 connected 0-16383\n");
                return;
            }
            writeSimple(output, "OK");
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
            writeBulk(output, "cluster");
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

        private void join(Thread thread) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
