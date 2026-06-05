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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ClusterFutureSyncInvocationHandlerTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void clusterSyncCommandsUseFutureInvocationHandlerAndNodeSelectionProxy() throws Exception {
        try (FakeRedisClusterServer server = new FakeRedisClusterServer()) {
            RedisClusterClient client = RedisClusterClient.create(server.redisUri());
            StatefulRedisClusterConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisAdvancedClusterCommands<String, String> commands = connection.sync();

                assertThat(commands.ping()).isEqualTo("PONG");

                NodeSelection<String, String> selection = commands.nodes(node -> true);
                assertThat(selection.size()).isEqualTo(1);
                assertThat(selection.node(0).getNodeId()).isEqualTo(server.nodeId());

                RedisClusterCommands<String, String> nodeCommands = commands.getConnection(server.nodeId());
                assertThat(nodeCommands.ping()).isEqualTo("PONG");
                assertThat(server.commands()).extracting(command -> command.get(0))
                        .contains("CLUSTER", "INFO", "PING");
            } finally {
                if (connection != null) {
                    connection.close();
                }
                client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            }
        }
    }

    private static final class FakeRedisClusterServer implements Closeable {
        private static final String NODE_ID = "0000000000000000000000000000000000000001";

        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private final Map<String, String> strings = new ConcurrentHashMap<>();
        private volatile boolean closed;

        FakeRedisClusterServer() throws Exception {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(500);
            acceptThread = new Thread(this::acceptConnections, "fake-redis-cluster-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        }

        RedisURI redisUri() {
            return RedisURI.Builder.redis(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort())
                    .withTimeout(COMMAND_TIMEOUT)
                    .build();
        }

        String nodeId() {
            return NODE_ID;
        }

        List<List<String>> commands() {
            return commands;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
            try {
                acceptThread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            clientExecutor.shutdownNow();
            try {
                clientExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void acceptConnections() {
            started.countDown();
            while (!closed) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(5_000);
                    clientExecutor.execute(() -> handleClient(socket));
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (!closed) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        private void handleClient(Socket socket) {
            try (Socket client = socket) {
                handle(client);
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
                case "INFO":
                    writeBulk(output, "connected_clients:1\nmaster_repl_offset:0\n");
                    break;
                case "CLUSTER":
                    writeClusterResponse(output, command);
                    break;
                case "SET":
                    strings.put(command.get(1), command.get(2));
                    writeSimple(output, "OK");
                    break;
                case "GET":
                    writeBulk(output, strings.get(command.get(1)));
                    break;
                case "AUTH":
                case "CLIENT":
                case "READONLY":
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
            if (command.size() > 1 && command.get(1).equalsIgnoreCase("NODES")) {
                writeBulk(output, clusterNodes());
                return;
            }
            writeSimple(output, "OK");
        }

        private String clusterNodes() {
            return NODE_ID + " " + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort()
                    + "@" + (serverSocket.getLocalPort() + 10_000)
                    + " myself,master - 0 0 1 connected 0-16383\n";
        }

        private void writeHelloResponse(OutputStream output) throws IOException {
            output.write("%4\r\n".getBytes(StandardCharsets.UTF_8));
            writeSimple(output, "id");
            writeInteger(output, 1);
            writeSimple(output, "mode");
            writeSimple(output, "cluster");
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
    }
}
